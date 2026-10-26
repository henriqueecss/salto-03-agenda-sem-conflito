package br.com.jacaranda.agenda.booking;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.jacaranda.agenda.error.BusinessRuleException;
import br.com.jacaranda.agenda.error.ErrorCode;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BookingResponse> create(@RequestBody CreateBooking request) {
        requireFilled(request);
        Booking booking = service.create(request);
        return ResponseEntity.created(URI.create("/bookings/" + booking.id())).body(response(booking));
    }

    @GetMapping("/{id}")
    BookingResponse get(@PathVariable UUID id) {
        return response(service.find(id));
    }

    @PostMapping("/{id}/cancel")
    BookingResponse cancel(@PathVariable UUID id) {
        return response(service.cancel(id));
    }

    private BookingResponse response(Booking booking) {
        return BookingResponse.of(booking, service.clinic().zone());
    }

    private static void requireFilled(CreateBooking request) {
        if (request == null || blank(request.professionalId()) || blank(request.serviceId())
                || blank(request.patientName()) || request.startsAt() == null) {
            throw new BusinessRuleException(ErrorCode.REQUISICAO_INVALIDA,
                    "Informe professionalId, serviceId, startsAt e patientName.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
