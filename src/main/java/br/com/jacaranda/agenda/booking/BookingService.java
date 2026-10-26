package br.com.jacaranda.agenda.booking;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.jacaranda.agenda.catalog.CatalogRepository;
import br.com.jacaranda.agenda.catalog.ClinicService;
import br.com.jacaranda.agenda.catalog.Professional;
import br.com.jacaranda.agenda.error.BusinessRuleException;
import br.com.jacaranda.agenda.error.ErrorCode;

@Service
public class BookingService {

    private final CatalogRepository catalog;
    private final BookingRepository bookings;
    private final BookingRules rules;
    private final ClinicRules clinic;
    private final Clock clock;

    public BookingService(CatalogRepository catalog, BookingRepository bookings, BookingRules rules,
            ClinicRules clinic, Clock clock) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.rules = rules;
        this.clinic = clinic;
        this.clock = clock;
    }

    public Booking create(CreateBooking request) {
        Professional professional = catalog.findProfessional(request.professionalId())
                .orElseThrow(() -> notFound("Profissional %s nao existe.".formatted(request.professionalId())));
        ClinicService service = catalog.findService(request.serviceId())
                .orElseThrow(() -> notFound("Servico %s nao existe.".formatted(request.serviceId())));

        var date = request.startsAt().toLocalDate();
        Slot slot = rules.validate(professional, service, request.startsAt(),
                catalog.isHoliday(date), catalog.isAbsent(professional.id(), date));

        Booking booking = new Booking(UUID.randomUUID(), professional.id(), service.id(), request.patientName(),
                slot.startsAt(), slot.endsAt(), Booking.Status.ACTIVE, clock.instant(), null);
        if (!bookings.insert(booking)) {
            throw new BusinessRuleException(ErrorCode.CONFLITO,
                    "O horario %s ja esta ocupado para %s.".formatted(request.startsAt(), professional.name()));
        }
        return booking;
    }

    public Booking find(UUID id) {
        return bookings.findById(id).orElseThrow(() -> notFound("Reserva %s nao existe.".formatted(id)));
    }

    public Booking cancel(UUID id) {
        if (!bookings.cancel(id, clock.instant())) {
            // 0 linhas afetadas: ou nao existe, ou ja estava cancelada
            find(id);
            throw new BusinessRuleException(ErrorCode.JA_CANCELADA, "A reserva %s ja foi cancelada.".formatted(id));
        }
        return find(id);
    }

    public ClinicRules clinic() {
        return clinic;
    }

    private static BusinessRuleException notFound(String detail) {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO, detail);
    }
}
