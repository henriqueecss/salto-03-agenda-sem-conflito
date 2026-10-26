package br.com.jacaranda.agenda.booking;

import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        String professionalId,
        String serviceId,
        String patientName,
        Instant startsAt,
        Instant endsAt,
        Status status,
        Instant createdAt,
        Instant cancelledAt) {

    public enum Status {
        ACTIVE, CANCELLED
    }
}
