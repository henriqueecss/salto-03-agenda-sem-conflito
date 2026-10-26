package br.com.jacaranda.agenda.booking;

import java.time.Instant;

/** Intervalo [startsAt, endsAt) ocupado por uma reserva. */
public record Slot(Instant startsAt, Instant endsAt) {
}
