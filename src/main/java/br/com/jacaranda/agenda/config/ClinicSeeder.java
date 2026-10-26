package br.com.jacaranda.agenda.config;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/** Carrega o catalogo da clinica (seed/clinica.json) na subida da aplicacao; repetir a carga e inofensivo. */
@Component
public class ClinicSeeder implements ApplicationRunner {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ClinicSeeder(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (InputStream in = new ClassPathResource("seed/clinica.json").getInputStream()) {
            seed(json.readValue(in, Seed.class));
        }
    }

    private void seed(Seed seed) {
        seed.holidays().forEach(h -> jdbc.sql("INSERT INTO holiday (date, name) VALUES (:d, :n) ON CONFLICT DO NOTHING")
                .param("d", h.date()).param("n", h.name()).update());
        seed.services().forEach(s -> jdbc.sql("""
                INSERT INTO service (id, name, duration_minutes, price) VALUES (:id, :n, :d, :p)
                ON CONFLICT DO NOTHING
                """)
                .param("id", s.id()).param("n", s.name()).param("d", s.durationMinutes()).param("p", s.price())
                .update());
        seed.professionals().forEach(this::seedProfessional);
    }

    private void seedProfessional(SeedProfessional p) {
        jdbc.sql("""
                INSERT INTO professional (id, name, work_start, work_end, lunch_start, lunch_end)
                VALUES (:id, :n, :ws, :we, :ls, :le) ON CONFLICT DO NOTHING
                """)
                .param("id", p.id()).param("n", p.name())
                .param("ws", p.workStart()).param("we", p.workEnd())
                .param("ls", p.lunchStart()).param("le", p.lunchEnd())
                .update();
        p.weekdays().forEach(d -> jdbc.sql("""
                INSERT INTO professional_weekday (professional_id, iso_weekday) VALUES (:p, :d)
                ON CONFLICT DO NOTHING
                """).param("p", p.id()).param("d", d).update());
        p.serviceIds().forEach(s -> jdbc.sql("""
                INSERT INTO professional_service (professional_id, service_id) VALUES (:p, :s)
                ON CONFLICT DO NOTHING
                """).param("p", p.id()).param("s", s).update());
        p.absences().forEach(a -> jdbc.sql("""
                INSERT INTO absence (professional_id, date, reason) VALUES (:p, :d, :r)
                ON CONFLICT DO NOTHING
                """).param("p", p.id()).param("d", a.date()).param("r", a.reason()).update());
    }

    record Seed(List<SeedHoliday> holidays, List<SeedService> services, List<SeedProfessional> professionals) {
    }

    record SeedHoliday(LocalDate date, String name) {
    }

    record SeedService(String id, String name, int durationMinutes, BigDecimal price) {
    }

    record SeedAbsence(LocalDate date, String reason) {
    }

    record SeedProfessional(String id, String name, List<String> serviceIds, List<Integer> weekdays,
            LocalTime workStart, LocalTime workEnd, LocalTime lunchStart, LocalTime lunchEnd,
            List<SeedAbsence> absences) {
    }
}
