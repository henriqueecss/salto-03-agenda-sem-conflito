package br.com.jacaranda.agenda.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import br.com.jacaranda.agenda.TestcontainersConfiguration;
import br.com.jacaranda.agenda.support.ApiClient;
import br.com.jacaranda.agenda.support.ApiClient.Reply;
import br.com.jacaranda.agenda.support.FixedClockConfig;
import br.com.jacaranda.agenda.support.Parallel;

/**
 * Prova de que a garantia mora no banco. O pool de conexoes e maior que o numero de threads: com um pool
 * pequeno as requisicoes seriam serializadas e a corrida nao aconteceria de verdade.
 */
@Import({TestcontainersConfiguration.class, FixedClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.maximum-pool-size=60")
class ConcurrencyTest {

    private static final int THREADS = 50;

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BookingRepository bookings;

    ApiClient api;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM booking").update();
        jdbc.sql("DROP TABLE IF EXISTS naive_booking").update();
        api = new ApiClient(port);
    }

    @AfterEach
    void tearDown() {
        jdbc.sql("DROP TABLE IF EXISTS naive_booking").update();
    }

    @RepeatedTest(20)
    void fiftyRequestsForTheSameSlotCreateExactlyOneBooking() throws Exception {
        List<Reply> replies = Parallel.fireTogether(THREADS, i ->
                () -> api.book("PRO2", "S1", "2026-11-05T15:00", "Paciente " + i));

        long created = replies.stream().filter(r -> r.status() == 201).count();
        List<Reply> rejected = replies.stream().filter(r -> r.status() != 201).toList();

        assertThat(created).isEqualTo(1);
        assertThat(rejected).hasSize(THREADS - 1)
                .allSatisfy(r -> {
                    assertThat(r.status()).isEqualTo(409);
                    assertThat(r.code()).isEqualTo("CONFLITO");
                });
        assertThat(activeBookings("PRO2")).isEqualTo(1);
    }

    @RepeatedTest(10)
    void partiallyOverlappingRequestsNeverLeaveTwoActiveBookingsOverlapping() throws Exception {
        Random random = new Random(42);
        String[] services = {"S1", "S2", "S3", "S5"};
        List<String> starts = new ArrayList<>();
        List<String> serviceIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int minutesAfterNine = random.nextInt(18) * 10; // 09:00 a 11:50, sempre multiplo de 10
            starts.add("2026-11-03T%02d:%02d".formatted(9 + minutesAfterNine / 60, minutesAfterNine % 60));
            serviceIds.add(services[random.nextInt(services.length)]);
        }

        List<Reply> replies = Parallel.fireTogether(THREADS, i ->
                () -> api.book("PRO1", serviceIds.get(i), starts.get(i), "Paciente " + i));

        assertThat(replies).allSatisfy(r -> assertThat(r.status()).isIn(201, 409, 422));
        long created = replies.stream().filter(r -> r.status() == 201).count();
        assertThat(created).isPositive();
        assertThat(activeBookings("PRO1")).isEqualTo(created);
        assertThat(overlappingActivePairs()).isZero();
    }

    /**
     * Contraprova: o "verifica e depois insere" sem constraint. A barreira entre a verificacao e a
     * insercao forca o pior intercalamento: todas as threads veem o horario livre e todas inserem.
     */
    @Test
    void checkThenInsertWithoutConstraintLetsEveryoneIn() throws Exception {
        jdbc.sql("""
                CREATE TABLE naive_booking (
                    id uuid PRIMARY KEY, professional_id text NOT NULL,
                    starts_at timestamptz NOT NULL, ends_at timestamptz NOT NULL)
                """).update();
        Instant start = Instant.parse("2026-11-05T18:00:00Z");
        Instant end = start.plusSeconds(30 * 60);
        CyclicBarrier betweenCheckAndInsert = new CyclicBarrier(THREADS);

        Parallel.fireTogether(THREADS, i -> () -> {
            int overlapping = jdbc.sql("""
                    SELECT count(*) FROM naive_booking
                    WHERE professional_id = 'PRO2' AND starts_at < :end AND ends_at > :start
                    """)
                    .param("start", java.sql.Timestamp.from(start)).param("end", java.sql.Timestamp.from(end))
                    .query(Integer.class).single();
            betweenCheckAndInsert.await();
            if (overlapping == 0) {
                jdbc.sql("INSERT INTO naive_booking VALUES (:id, 'PRO2', :start, :end)")
                        .param("id", UUID.randomUUID())
                        .param("start", java.sql.Timestamp.from(start)).param("end", java.sql.Timestamp.from(end))
                        .update();
            }
            return null;
        });

        int rows = jdbc.sql("SELECT count(*) FROM naive_booking").query(Integer.class).single();
        assertThat(rows).isEqualTo(THREADS);
    }

    /** Mesmo intercalamento forcado da contraprova, agora contra a tabela real: a constraint segura. */
    @Test
    void sameForcedInterleavingAgainstTheRealTableStillCreatesOne() throws Exception {
        Instant start = Instant.parse("2026-11-05T18:00:00Z");
        Instant end = start.plusSeconds(30 * 60);
        CyclicBarrier betweenCheckAndInsert = new CyclicBarrier(THREADS);

        List<Boolean> inserted = Parallel.fireTogether(THREADS, i -> () -> {
            boolean looksFree = bookings.findActiveOnDay("PRO2", start, end).isEmpty();
            betweenCheckAndInsert.await();
            return looksFree && bookings.insert(new Booking(UUID.randomUUID(), "PRO2", "S1", "Paciente " + i,
                    start, end, Booking.Status.ACTIVE, Instant.now(), null));
        });

        assertThat(inserted.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(activeBookings("PRO2")).isEqualTo(1);
    }

    private long activeBookings(String professionalId) {
        return jdbc.sql("SELECT count(*) FROM booking WHERE professional_id = :p AND status = 'ACTIVE'")
                .param("p", professionalId).query(Long.class).single();
    }

    private long overlappingActivePairs() {
        return jdbc.sql("""
                SELECT count(*) FROM booking a JOIN booking b
                  ON a.professional_id = b.professional_id AND a.id < b.id
                 AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
                 AND a.starts_at < b.ends_at AND b.starts_at < a.ends_at
                """).query(Long.class).single();
    }
}
