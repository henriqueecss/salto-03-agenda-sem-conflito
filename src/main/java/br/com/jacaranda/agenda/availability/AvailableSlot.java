package br.com.jacaranda.agenda.availability;

import java.time.OffsetDateTime;

public record AvailableSlot(String professionalId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
}
