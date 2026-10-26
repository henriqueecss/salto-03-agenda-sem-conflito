package br.com.jacaranda.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingExclusionConstraintTest {

    private static final String EXCLUSION_VIOLATION = "23P01";

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM booking").update();
        jdbc.sql("""
                INSERT INTO professional (id, name, work_start, work_end, lunch_start, lunch_end)
                VALUES ('PRO1', 'Dr. Otavio Menezes', '09:00', '19:00', '12:00', '13:00'),
                       ('PRO2', 'Dra. Helena Albuquerque', '10:00', '20:00', '14:00', '15:00')
                ON CONFLICT DO NOTHING
                """).update();
        jdbc.sql("""
                INSERT INTO service (id, name, duration_minutes, price)
                VALUES ('S1', 'Consulta', 30, 45.00)
                ON CONFLICT DO NOTHING
                """).update();
    }

    @Test
    void rejectsOverlappingActiveBookingsOfSameProfessional() {
        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        assertThatThrownBy(() -> insert("PRO1", "2026-11-03T09:20:00-03:00", "2026-11-03T09:50:00-03:00", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e.getCause()).getSQLState()).isEqualTo(EXCLUSION_VIOLATION));
    }

    @Test
    void allowsBookingStartingExactlyWhenAnotherEnds() {
        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        insert("PRO1", "2026-11-03T09:30:00-03:00", "2026-11-03T10:00:00-03:00", "ACTIVE");

        assertThat(count("PRO1")).isEqualTo(2);
    }

    @Test
    void allowsSameTimeForDifferentProfessionals() {
        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        insert("PRO2", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        assertThat(count("PRO1")).isEqualTo(1);
        assertThat(count("PRO2")).isEqualTo(1);
    }

    @Test
    void cancelledBookingReleasesTheSlot() {
        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "CANCELLED");

        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        assertThat(count("PRO1")).isEqualTo(2);
    }

    @Test
    void rejectsReactivatingIntoAnOccupiedSlot() {
        UUID cancelled = insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "CANCELLED");
        insert("PRO1", "2026-11-03T09:00:00-03:00", "2026-11-03T09:30:00-03:00", "ACTIVE");

        assertThatThrownBy(() -> jdbc.sql("UPDATE booking SET status = 'ACTIVE' WHERE id = :id")
                .param("id", cancelled).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBookingEndingBeforeItStarts() {
        assertThatThrownBy(() -> insert("PRO1", "2026-11-03T09:30:00-03:00", "2026-11-03T09:00:00-03:00", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insert(String professionalId, String startsAt, String endsAt, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO booking (id, professional_id, service_id, patient_name, starts_at, ends_at, status)
                VALUES (:id, :professional, 'S1', 'Paciente Teste', :startsAt, :endsAt, :status)
                """)
                .param("id", id)
                .param("professional", professionalId)
                .param("startsAt", OffsetDateTime.parse(startsAt))
                .param("endsAt", OffsetDateTime.parse(endsAt))
                .param("status", status)
                .update();
        return id;
    }

    private int count(String professionalId) {
        return jdbc.sql("SELECT count(*) FROM booking WHERE professional_id = :p")
                .param("p", professionalId).query(Integer.class).single();
    }
}
