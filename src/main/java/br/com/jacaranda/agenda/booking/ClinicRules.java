package br.com.jacaranda.agenda.booking;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("agenda")
public record ClinicRules(ZoneId zone, int minNoticeMinutes, int maxNoticeDays, int startStepMinutes) {
}
