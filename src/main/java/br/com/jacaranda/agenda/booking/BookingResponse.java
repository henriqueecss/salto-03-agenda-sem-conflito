package br.com.jacaranda.agenda.booking;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String professionalId,
        String serviceId,
        String patientName,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Booking.Status status) {

    static BookingResponse of(Booking booking, ZoneId zone) {
        return new BookingResponse(booking.id(), booking.professionalId(), booking.serviceId(),
                booking.patientName(),
                booking.startsAt().atZone(zone).toOffsetDateTime(),
                booking.endsAt().atZone(zone).toOffsetDateTime(),
                booking.status());
    }
}
