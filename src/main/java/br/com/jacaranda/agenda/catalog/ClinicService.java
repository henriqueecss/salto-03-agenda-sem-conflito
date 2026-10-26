package br.com.jacaranda.agenda.catalog;

import java.math.BigDecimal;

public record ClinicService(String id, String name, int durationMinutes, BigDecimal price) {
}
