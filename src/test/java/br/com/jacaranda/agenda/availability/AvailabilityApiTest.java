package br.com.jacaranda.agenda.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.JsonNode;

@Import({TestcontainersConfiguration.class, FixedClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AvailabilityApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    ApiClient api;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM booking").update();
        api = new ApiClient(port);
    }

    @Test
    void listsEveryTenMinuteStartThatFitsAndSkipsLunch() {
        Reply reply = api.get("/availability?serviceId=S1&date=2026-11-05&professionalId=PRO2");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().path("date").asString()).isEqualTo("2026-11-05");
        assertThat(reply.body().path("serviceId").asString()).isEqualTo("S1");
        assertThat(reply.body().path("durationMinutes").asInt()).isEqualTo(30);

        List<String> starts = starts(reply);
        assertThat(starts).hasSize(50)
                .startsWith("2026-11-05T10:00:00-03:00")
                .contains("2026-11-05T13:30:00-03:00", "2026-11-05T15:00:00-03:00", "2026-11-05T19:30:00-03:00")
                .doesNotContain("2026-11-05T13:40:00-03:00", "2026-11-05T14:30:00-03:00", "2026-11-05T19:40:00-03:00");
        assertThat(reply.body().path("slots").get(0).path("endsAt").asString()).isEqualTo("2026-11-05T10:30:00-03:00");
    }

    @Test
    void activeBookingRemovesOverlappingStartsButKeepsTouchingOnes() {
        api.book("PRO1", "S1", "2026-11-03T09:00", "Maria");

        List<String> starts = starts(api.get("/availability?serviceId=S1&date=2026-11-03&professionalId=PRO1"));

        assertThat(starts).startsWith("2026-11-03T09:30:00-03:00")
                .doesNotContain("2026-11-03T09:00:00-03:00", "2026-11-03T09:20:00-03:00");
    }

    @Test
    void cancelledBookingFreesTheStarts() {
        String id = api.book("PRO1", "S1", "2026-11-03T09:00", "Maria").body().path("id").asString();
        api.cancel(id);

        List<String> starts = starts(api.get("/availability?serviceId=S1&date=2026-11-03&professionalId=PRO1"));

        assertThat(starts).startsWith("2026-11-03T09:00:00-03:00");
    }

    @Test
    void withoutProfessionalListsEveryoneWhoOffersTheService() {
        Reply reply = api.get("/availability?serviceId=S4&date=2026-11-05");

        assertThat(reply.body().path("slots")).isNotEmpty()
                .allSatisfy(slot -> assertThat(slot.path("professionalId").asString()).isEqualTo("PRO2"));

        List<String> professionals = new ArrayList<>();
        api.get("/availability?serviceId=S1&date=2026-11-05").body().path("slots")
                .forEach(slot -> professionals.add(slot.path("professionalId").asString()));
        assertThat(professionals).contains("PRO1", "PRO2", "PRO3");
    }

    @Test
    void closedDaysHaveNoSlots() {
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-11-02"))).isEmpty();
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-11-06&professionalId=PRO3"))).isEmpty();
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-11-09&professionalId=PRO2"))).isEmpty();
    }

    @Test
    void respectsMinimumNoticeAndMaximumHorizon() {
        // agora: 31/10 08:30, entao o primeiro inicio possivel de PRO1 e 09:30
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-10-31&professionalId=PRO1")))
                .startsWith("2026-10-31T09:30:00-03:00");
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-12-01&professionalId=PRO1"))).isEmpty();
        assertThat(starts(api.get("/availability?serviceId=S1&date=2026-10-28&professionalId=PRO1"))).isEmpty();
    }

    @Test
    void everyListedSlotCanActuallyBeBooked() {
        List<String> starts = starts(api.get("/availability?serviceId=S3&date=2026-11-03&professionalId=PRO1"));
        assertThat(starts).isNotEmpty();

        String first = starts.getFirst().substring(0, 16);
        assertThat(api.book("PRO1", "S3", first, "Maria").status()).isEqualTo(201);
    }

    @Test
    void rejectsMissingParametersAndUnknownIds() {
        assertThat(api.get("/availability?date=2026-11-05").status()).isEqualTo(400);
        assertThat(api.get("/availability?serviceId=S1").status()).isEqualTo(400);
        assertThat(api.get("/availability?serviceId=S1&date=quarta").status()).isEqualTo(400);
        assertThat(api.get("/availability?serviceId=S9&date=2026-11-05").status()).isEqualTo(404);
        assertThat(api.get("/availability?serviceId=S1&date=2026-11-05&professionalId=PRO9").status()).isEqualTo(404);
    }

    private static List<String> starts(Reply reply) {
        List<String> starts = new ArrayList<>();
        for (JsonNode slot : reply.body().path("slots")) {
            starts.add(slot.path("startsAt").asString());
        }
        return starts;
    }
}
