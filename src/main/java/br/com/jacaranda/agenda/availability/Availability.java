package br.com.jacaranda.agenda.availability;

import java.time.LocalDate;
import java.util.List;

public record Availability(LocalDate date, String serviceId, int durationMinutes, List<AvailableSlot> slots) {
}
