package br.com.jacaranda.agenda.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import br.com.jacaranda.agenda.catalog.ClinicService;
import br.com.jacaranda.agenda.catalog.Professional;
import br.com.jacaranda.agenda.error.BusinessRuleException;
import br.com.jacaranda.agenda.error.ErrorCode;

/**
 * Regras de negocio que nao dependem de outras reservas, na ordem de prioridade dos erros.
 * O conflito com outras reservas nao esta aqui: quem garante e a constraint de exclusao do banco.
 */
@Component
public class BookingRules {

    private final Clock clock;
    private final ClinicRules rules;

    public BookingRules(Clock clock, ClinicRules rules) {
        this.clock = clock;
        this.rules = rules;
    }

    public Slot validate(Professional professional, ClinicService service, LocalDateTime start,
            boolean holiday, boolean absent) {
        if (start.getMinute() % rules.startStepMinutes() != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw new BusinessRuleException(ErrorCode.HORARIO_INVALIDO,
                    "O horário deve começar em múltiplos de %d minutos.".formatted(rules.startStepMinutes()));
        }
        if (!professional.serviceIds().contains(service.id())) {
            throw new BusinessRuleException(ErrorCode.SERVICO_NAO_OFERECIDO,
                    "%s não realiza o serviço %s.".formatted(professional.name(), service.name()));
        }

        ZonedDateTime startsAt = start.atZone(rules.zone());
        checkNotice(startsAt);
        checkOpenDay(professional, start, holiday, absent);

        LocalDateTime end = start.plusMinutes(service.durationMinutes());
        checkWorkHours(professional, start, end);

        return new Slot(startsAt.toInstant(), end.atZone(rules.zone()).toInstant());
    }

    private void checkNotice(ZonedDateTime startsAt) {
        ZonedDateTime now = clock.instant().atZone(rules.zone());
        Instant earliest = now.plus(Duration.ofMinutes(rules.minNoticeMinutes())).toInstant();
        Instant latest = now.plusDays(rules.maxNoticeDays()).toInstant();
        if (startsAt.toInstant().isBefore(earliest)) {
            throw new BusinessRuleException(ErrorCode.ANTECEDENCIA_INVALIDA,
                    "A reserva precisa ser feita com pelo menos %d minutos de antecedência."
                            .formatted(rules.minNoticeMinutes()));
        }
        if (startsAt.toInstant().isAfter(latest)) {
            throw new BusinessRuleException(ErrorCode.ANTECEDENCIA_INVALIDA,
                    "A reserva pode ser feita com no máximo %d dias de antecedência.".formatted(rules.maxNoticeDays()));
        }
    }

    private void checkOpenDay(Professional professional, LocalDateTime start, boolean holiday, boolean absent) {
        int isoWeekday = start.getDayOfWeek().getValue();
        if (holiday || absent || !professional.weekdays().contains(isoWeekday)) {
            throw new BusinessRuleException(ErrorCode.DIA_FECHADO,
                    "%s não atende em %s.".formatted(professional.name(), start.toLocalDate()));
        }
    }

    private void checkWorkHours(Professional professional, LocalDateTime start, LocalDateTime end) {
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        boolean endsSameDay = end.toLocalDate().equals(start.toLocalDate());
        boolean insideWorkHours = !startTime.isBefore(professional.workStart())
                && endsSameDay && !endTime.isAfter(professional.workEnd());
        boolean touchesLunch = startTime.isBefore(professional.lunchEnd())
                && (!endsSameDay || endTime.isAfter(professional.lunchStart()));
        if (!insideWorkHours || touchesLunch) {
            throw new BusinessRuleException(ErrorCode.FORA_DO_EXPEDIENTE,
                    "O atendimento precisa caber no expediente (%s a %s) e fora do almoço (%s a %s)."
                            .formatted(professional.workStart(), professional.workEnd(),
                                    professional.lunchStart(), professional.lunchEnd()));
        }
    }
}
