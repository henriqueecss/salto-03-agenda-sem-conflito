package br.com.jacaranda.agenda.booking;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class BookingRepository {

    private static final String EXCLUSION_VIOLATION = "23P01";

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public BookingRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /**
     * Insere a reserva ativa. Quem decide se ha sobreposicao e a constraint de exclusao do banco.
     *
     * <p>Antes do INSERT a transacao pega um lock consultivo do profissional. Ele nao e a garantia (a
     * constraint continua sendo), so evita um deadlock: a constraint e checada depois que a linha entra no
     * indice, entao duas insercoes concorrentes que se sobrepoem enxergam uma a outra, esperam uma pela
     * outra e o Postgres aborta uma delas por deadlock apos 1 s. Com o lock, elas entram em fila, e a
     * segunda recebe o erro de exclusao na hora.
     *
     * @return false se o horario ja esta ocupado por outra reserva ativa do mesmo profissional
     */
    public boolean insert(Booking booking) {
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:professional))")
                        .param("professional", booking.professionalId())
                        .query((rs, n) -> 1).list();
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
            });
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

    public List<Slot> findActiveOnDay(String professionalId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT starts_at, ends_at FROM booking
                WHERE professional_id = :p AND status = 'ACTIVE' AND starts_at < :to AND ends_at > :from
                """)
                .param("p", professionalId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .query((rs, n) -> new Slot(rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant()))
                .list();
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
