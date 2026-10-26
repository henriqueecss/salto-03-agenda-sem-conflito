package br.com.jacaranda.agenda.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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

@Import({TestcontainersConfiguration.class, FixedClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiTest {

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
    void createdBookingCanBeReadBackWithOffsetAndLocation() {
        Reply created = api.book("PRO1", "S1", "2026-11-03T09:00", "Maria");

        assertThat(created.status()).isEqualTo(201);
        String id = created.body().path("id").asString();
        assertThat(created.body().path("startsAt").asString()).isEqualTo("2026-11-03T09:00:00-03:00");
        assertThat(created.body().path("endsAt").asString()).isEqualTo("2026-11-03T09:30:00-03:00");

        Reply read = api.get("/bookings/" + id);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body().path("patientName").asString()).isEqualTo("Maria");
    }

    @Test
    void rejectsMissingFields() {
        Reply reply = api.post("/bookings", java.util.Map.of("professionalId", "PRO1"));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.code()).isEqualTo("REQUISICAO_INVALIDA");
    }

    @Test
    void rejectsUnparseableStartTime() {
        Reply reply = api.book("PRO1", "S1", "amanha de manha", "Maria");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.code()).isEqualTo("REQUISICAO_INVALIDA");
    }

    @Test
    void unknownProfessionalIsNotFound() {
        Reply reply = api.book("PRO9", "S1", "2026-11-03T09:00", "Maria");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.code()).isEqualTo("RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void cancellingUnknownBookingIsNotFound() {
        Reply reply = api.cancel(UUID.randomUUID().toString());

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.code()).isEqualTo("RECURSO_NAO_ENCONTRADO");
    }
}
