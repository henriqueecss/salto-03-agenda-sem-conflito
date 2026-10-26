package br.com.jacaranda.agenda.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jacaranda.agenda.booking.ClinicRules;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(ClinicRules rules) {
        return Clock.system(rules.zone());
    }
}
