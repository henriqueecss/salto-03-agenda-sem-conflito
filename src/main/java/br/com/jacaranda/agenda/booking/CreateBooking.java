package br.com.jacaranda.agenda.booking;

import java.time.LocalDateTime;

/** Pedido de reserva; {@code startsAt} e horario local da clinica, sem offset. */
public record CreateBooking(String professionalId, String serviceId, LocalDateTime startsAt, String patientName) {
}
