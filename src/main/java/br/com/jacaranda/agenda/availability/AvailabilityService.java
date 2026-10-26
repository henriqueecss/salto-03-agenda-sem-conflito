package br.com.jacaranda.agenda.availability;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.jacaranda.agenda.booking.BookingRepository;
import br.com.jacaranda.agenda.booking.BookingRules;
import br.com.jacaranda.agenda.booking.ClinicRules;
import br.com.jacaranda.agenda.booking.Slot;
import br.com.jacaranda.agenda.catalog.CatalogRepository;
import br.com.jacaranda.agenda.catalog.ClinicService;
import br.com.jacaranda.agenda.catalog.Professional;
import br.com.jacaranda.agenda.error.BusinessRuleException;
import br.com.jacaranda.agenda.error.ErrorCode;

/**
 * Calcula os horarios livres na hora: cada inicio candidato passa pelas mesmas regras da reserva
 * e nao pode sobrepor reserva ativa. E so informativo; quem garante a ausencia de conflito e o banco.
 */
@Service
public class AvailabilityService {

    private final CatalogRepository catalog;
    private final BookingRepository bookings;
    private final BookingRules rules;
    private final ClinicRules clinic;

    public AvailabilityService(CatalogRepository catalog, BookingRepository bookings, BookingRules rules,
            ClinicRules clinic) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.rules = rules;
        this.clinic = clinic;
    }

    public Availability find(String serviceId, LocalDate date, String professionalId) {
        ClinicService service = catalog.findService(serviceId).orElseThrow(() -> notFound(
                "Serviço %s não existe.".formatted(serviceId)));
        List<Professional> professionals = professionalId == null
                ? catalog.findProfessionals()
                : List.of(catalog.findProfessional(professionalId).orElseThrow(() -> notFound(
                        "Profissional %s não existe.".formatted(professionalId))));

        List<AvailableSlot> slots = new ArrayList<>();
        for (Professional professional : professionals) {
            if (professional.serviceIds().contains(service.id())) {
                slots.addAll(slotsOf(professional, service, date));
            }
        }
        return new Availability(date, service.id(), service.durationMinutes(), slots);
    }

    private List<AvailableSlot> slotsOf(Professional professional, ClinicService service, LocalDate date) {
        boolean holiday = catalog.isHoliday(date);
        boolean absent = catalog.isAbsent(professional.id(), date);
        List<Slot> taken = bookings.findActiveOnDay(professional.id(),
                date.atStartOfDay(clinic.zone()).toInstant(),
                date.plusDays(1).atStartOfDay(clinic.zone()).toInstant());

        List<AvailableSlot> free = new ArrayList<>();
        LocalDateTime lastStart = date.atTime(professional.workEnd()).minusMinutes(service.durationMinutes());
        for (LocalDateTime start = date.atTime(professional.workStart());
                !start.isAfter(lastStart);
                start = start.plusMinutes(clinic.startStepMinutes())) {
            try {
                Slot slot = rules.validate(professional, service, start, holiday, absent);
                if (taken.stream().noneMatch(t -> overlaps(t, slot))) {
                    free.add(new AvailableSlot(professional.id(),
                            slot.startsAt().atZone(clinic.zone()).toOffsetDateTime(),
                            slot.endsAt().atZone(clinic.zone()).toOffsetDateTime()));
                }
            } catch (BusinessRuleException e) {
                // inicio que a reserva recusaria: nao e horario livre
            }
        }
        return free;
    }

    private static boolean overlaps(Slot a, Slot b) {
        return a.startsAt().isBefore(b.endsAt()) && b.startsAt().isBefore(a.endsAt());
    }

    private static BusinessRuleException notFound(String detail) {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO, detail);
    }
}
