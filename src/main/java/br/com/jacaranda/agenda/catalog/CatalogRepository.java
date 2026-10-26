package br.com.jacaranda.agenda.catalog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClinicService> findServices() {
        return jdbc.sql("SELECT id, name, duration_minutes, price FROM service ORDER BY id")
                .query((rs, n) -> new ClinicService(
                        rs.getString("id"), rs.getString("name"),
                        rs.getInt("duration_minutes"), rs.getBigDecimal("price")))
                .list();
    }

    public Optional<ClinicService> findService(String id) {
        return jdbc.sql("SELECT id, name, duration_minutes, price FROM service WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new ClinicService(
                        rs.getString("id"), rs.getString("name"),
                        rs.getInt("duration_minutes"), rs.getBigDecimal("price")))
                .optional();
    }

    public List<Professional> findProfessionals() {
        return jdbc.sql("SELECT id FROM professional ORDER BY id").query(String.class).list().stream()
                .map(id -> findProfessional(id).orElseThrow())
                .toList();
    }

    public Optional<Professional> findProfessional(String id) {
        // As listas saem em consultas separadas, depois que a primeira devolveu a conexao ao pool:
        // pedir outra conexao de dentro do mapeador de linhas deixa cada pedido segurando uma e esperando
        // a segunda, e com mais pedidos que conexoes o pool trava.
        return jdbc.sql("""
                SELECT id, name, work_start, work_end, lunch_start, lunch_end
                FROM professional WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new ProfessionalRow(
                        rs.getString("id"), rs.getString("name"),
                        rs.getObject("work_start", LocalTime.class), rs.getObject("work_end", LocalTime.class),
                        rs.getObject("lunch_start", LocalTime.class), rs.getObject("lunch_end", LocalTime.class)))
                .optional()
                .map(row -> new Professional(row.id(), row.name(), row.workStart(), row.workEnd(),
                        row.lunchStart(), row.lunchEnd(), weekdaysOf(id), serviceIdsOf(id)));
    }

    private record ProfessionalRow(String id, String name, LocalTime workStart, LocalTime workEnd,
            LocalTime lunchStart, LocalTime lunchEnd) {
    }

    public boolean isHoliday(LocalDate date) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM holiday WHERE date = :d)")
                .param("d", date).query(Boolean.class).single();
    }

    public boolean isAbsent(String professionalId, LocalDate date) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM absence WHERE professional_id = :p AND date = :d)")
                .param("p", professionalId).param("d", date).query(Boolean.class).single();
    }

    private List<Integer> weekdaysOf(String professionalId) {
        return jdbc.sql("SELECT iso_weekday FROM professional_weekday WHERE professional_id = :id ORDER BY iso_weekday")
                .param("id", professionalId).query(Integer.class).list();
    }

    private List<String> serviceIdsOf(String professionalId) {
        return jdbc.sql("SELECT service_id FROM professional_service WHERE professional_id = :id ORDER BY service_id")
                .param("id", professionalId).query(String.class).list();
    }
}
