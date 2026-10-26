package br.com.jacaranda.agenda.booking;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private static final String EXCLUSION_VIOLATION = "23P01";

    private final JdbcClient jdbc;

    public BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insere a reserva ativa. Quem decide se ha sobreposicao e a constraint de exclusao do banco.
     *
     * @return false se o horario ja esta ocupado por outra reserva ativa do mesmo profissional
     */
    public boolean insert(Booking booking) {
        try {
            jdbc.sql("""
                    INSERT INTO booking (id, professional_id, service_id, patient_name, starts_at, ends_at, status, created_at)
                    VALUES (:id, :professional, :service, :patient, :startsAt, :endsAt, 'ACTIVE', :createdAt)
                    """)
                    .param("id", booking.id())
                    .param("professional", booking.professionalId())
                    .param("service", booking.serviceId())
                    .param("patient", booking.patientName())
                    .param("startsAt", java.sql.Timestamp.from(booking.startsAt()))
                    .param("endsAt", java.sql.Timestamp.from(booking.endsAt()))
                    .param("createdAt", java.sql.Timestamp.from(booking.createdAt()))
                    .update();
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                return false;
            }
            throw e;
        }
    }

    public Optional<Booking> findById(UUID id) {
        return jdbc.sql("""
                SELECT id, professional_id, service_id, patient_name, starts_at, ends_at, status, created_at, cancelled_at
                FROM booking WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new Booking(
                        rs.getObject("id", UUID.class),
                        rs.getString("professional_id"),
                        rs.getString("service_id"),
                        rs.getString("patient_name"),
                        rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at").toInstant(),
                        Booking.Status.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("cancelled_at") == null ? null : rs.getTimestamp("cancelled_at").toInstant()))
                .optional();
    }

    /**
     * Cancela apenas se ainda estiver ativa, em um unico UPDATE condicional: dois cancelamentos
     * simultaneos nunca passam os dois.
     *
     * @return false se a reserva nao existe ou ja estava cancelada
     */
    public boolean cancel(UUID id, Instant when) {
        return jdbc.sql("""
                UPDATE booking SET status = 'CANCELLED', cancelled_at = :when
                WHERE id = :id AND status = 'ACTIVE'
                """)
                .param("id", id)
                .param("when", java.sql.Timestamp.from(when))
                .update() == 1;
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        return cause instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState());
    }
}
