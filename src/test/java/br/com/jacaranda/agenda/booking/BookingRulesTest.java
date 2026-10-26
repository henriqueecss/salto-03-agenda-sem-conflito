package br.com.jacaranda.agenda.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.jacaranda.agenda.catalog.ClinicService;
import br.com.jacaranda.agenda.catalog.Professional;
import br.com.jacaranda.agenda.error.BusinessRuleException;
import br.com.jacaranda.agenda.error.ErrorCode;

class BookingRulesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock NOW = Clock.fixed(OffsetDateTime.parse("2026-10-31T08:30:00-03:00").toInstant(), ZONE);

    private static final ClinicService CONSULTATION = new ClinicService("S1", "Consulta", 30, new BigDecimal("45.00"));
    private static final ClinicService FULL_EVALUATION = new ClinicService("S3", "Avaliacao", 50, new BigDecimal("70.00"));
    private static final ClinicService PROCEDURE = new ClinicService("S4", "Procedimento", 90, new BigDecimal("120.00"));

    private static final Professional OTAVIO = new Professional("PRO1", "Dr. Otavio",
            LocalTime.of(9, 0), LocalTime.of(19, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
            List.of(1, 2, 3, 4, 5, 6), List.of("S1", "S2", "S3", "S5"));
    private static final Professional HELENA = new Professional("PRO2", "Dra. Helena",
            LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(14, 0), LocalTime.of(15, 0),
            List.of(2, 3, 4, 5, 6), List.of("S1", "S2", "S3", "S4", "S5"));

    private final BookingRules rules = new BookingRules(NOW, new ClinicRules(ZONE, 60, 30, 10));

    @Test
    void acceptsValidBookingAndReturnsTheOccupiedInstants() {
        Slot slot = validate(OTAVIO, CONSULTATION, "2026-11-03T09:00");

        assertThat(slot.startsAt()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
        assertThat(slot.endsAt()).isEqualTo(Instant.parse("2026-11-03T12:30:00Z"));
    }

    @Test
    void rejectsStartNotMultipleOfTenMinutes() {
        assertCode(ErrorCode.HORARIO_INVALIDO, validateCatching(OTAVIO, CONSULTATION, "2026-11-05T10:15"));
    }

    @Test
    void rejectsServiceTheProfessionalDoesNotOffer() {
        assertCode(ErrorCode.SERVICO_NAO_OFERECIDO, validateCatching(OTAVIO, PROCEDURE, "2026-11-04T10:00"));
    }

    @Test
    void rejectsLessThanOneHourAhead() {
        assertCode(ErrorCode.ANTECEDENCIA_INVALIDA, validateCatching(OTAVIO, CONSULTATION, "2026-10-31T09:00"));
    }

    @Test
    void acceptsExactlyOneHourAhead() {
        assertThat(validate(OTAVIO, CONSULTATION, "2026-10-31T09:30")).isNotNull();
    }

    @Test
    void rejectsPast() {
        assertCode(ErrorCode.ANTECEDENCIA_INVALIDA, validateCatching(OTAVIO, CONSULTATION, "2026-10-28T10:00"));
    }

    @Test
    void rejectsMoreThanThirtyDaysAhead() {
        assertCode(ErrorCode.ANTECEDENCIA_INVALIDA, validateCatching(OTAVIO, CONSULTATION, "2026-12-01T10:00"));
    }

    @Test
    void acceptsExactlyThirtyDaysAhead() {
        Clock tenAm = Clock.fixed(OffsetDateTime.parse("2026-10-31T10:00:00-03:00").toInstant(), ZONE);
        BookingRules atTenAm = new BookingRules(tenAm, new ClinicRules(ZONE, 60, 30, 10));

        assertThat(atTenAm.validate(OTAVIO, CONSULTATION, start("2026-11-30T10:00"), false, false)).isNotNull();
        assertCode(ErrorCode.ANTECEDENCIA_INVALIDA,
                catching(() -> atTenAm.validate(OTAVIO, CONSULTATION, start("2026-11-30T10:10"), false, false)));
    }

    @Test
    void rejectsHoliday() {
        assertCode(ErrorCode.DIA_FECHADO,
                catching(() -> rules.validate(OTAVIO, CONSULTATION, start("2026-11-02T10:00"), true, false)));
    }

    @Test
    void rejectsAbsence() {
        assertCode(ErrorCode.DIA_FECHADO,
                catching(() -> rules.validate(OTAVIO, CONSULTATION, start("2026-11-06T10:00"), false, true)));
    }

    @Test
    void rejectsWeekdayTheProfessionalDoesNotWork() {
        assertCode(ErrorCode.DIA_FECHADO, validateCatching(HELENA, CONSULTATION, "2026-11-09T10:00"));
    }

    @Test
    void rejectsBookingThatInvadesLunch() {
        assertCode(ErrorCode.FORA_DO_EXPEDIENTE, validateCatching(OTAVIO, FULL_EVALUATION, "2026-11-03T11:30"));
    }

    @Test
    void acceptsBookingEndingExactlyWhenLunchStartsAndStartingWhenItEnds() {
        assertThat(validate(OTAVIO, CONSULTATION, "2026-11-03T11:30")).isNotNull();
        assertThat(validate(OTAVIO, CONSULTATION, "2026-11-03T13:00")).isNotNull();
    }

    @Test
    void rejectsBookingEndingAfterClosing() {
        assertCode(ErrorCode.FORA_DO_EXPEDIENTE, validateCatching(OTAVIO, FULL_EVALUATION, "2026-11-03T18:20"));
    }

    @Test
    void acceptsBookingEndingExactlyAtClosing() {
        assertThat(validate(OTAVIO, FULL_EVALUATION, "2026-11-03T18:10")).isNotNull();
    }

    @Test
    void rejectsBookingStartingBeforeWorkHours() {
        ClinicService triage = new ClinicService("S5", "Triagem", 10, new BigDecimal("15.00"));
        assertCode(ErrorCode.FORA_DO_EXPEDIENTE, validateCatching(HELENA, triage, "2026-11-04T09:50"));
    }

    @Test
    void reportsFirstFailureInPriorityOrder() {
        // horario invalido + servico nao oferecido + antecedencia + feriado: vence o horario
        assertCode(ErrorCode.HORARIO_INVALIDO, catching(() ->
                rules.validate(OTAVIO, PROCEDURE, start("2026-10-28T10:15"), true, false)));
        // servico nao oferecido + antecedencia + feriado: vence o servico
        assertCode(ErrorCode.SERVICO_NAO_OFERECIDO, catching(() ->
                rules.validate(OTAVIO, PROCEDURE, start("2026-10-28T10:00"), true, false)));
        // antecedencia + feriado + fora do expediente: vence a antecedencia
        assertCode(ErrorCode.ANTECEDENCIA_INVALIDA, catching(() ->
                rules.validate(OTAVIO, CONSULTATION, start("2026-10-28T07:00"), true, false)));
        // feriado + fora do expediente: vence o dia fechado
        assertCode(ErrorCode.DIA_FECHADO, catching(() ->
                rules.validate(OTAVIO, CONSULTATION, start("2026-11-02T07:00"), true, false)));
    }

    private Slot validate(Professional professional, ClinicService service, String localStart) {
        return rules.validate(professional, service, start(localStart), false, false);
    }

    private BusinessRuleException validateCatching(Professional professional, ClinicService service, String localStart) {
        return catching(() -> validate(professional, service, localStart));
    }

    private static BusinessRuleException catching(Runnable action) {
        return (BusinessRuleException) assertThatThrownBy(action::run)
                .isInstanceOf(BusinessRuleException.class)
                .actual();
    }

    private static void assertCode(ErrorCode expected, BusinessRuleException actual) {
        assertThat(actual.code()).isEqualTo(expected);
    }

    private static LocalDateTime start(String localDateTime) {
        return LocalDateTime.parse(localDateTime);
    }
}
