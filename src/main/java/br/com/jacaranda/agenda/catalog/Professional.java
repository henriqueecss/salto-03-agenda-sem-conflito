package br.com.jacaranda.agenda.catalog;

import java.time.LocalTime;
import java.util.List;

public record Professional(
        String id,
        String name,
        LocalTime workStart,
        LocalTime workEnd,
        LocalTime lunchStart,
        LocalTime lunchEnd,
        List<Integer> weekdays,
        List<String> serviceIds) {
}
