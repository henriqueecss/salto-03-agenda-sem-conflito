package br.com.jacaranda.agenda.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import tools.jackson.databind.json.JsonMapper;

/** Congela o relogio no "agora" dos testes definido em data/barbearia.json (2026-10-31T08:30-03:00). */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() throws IOException {
        String now = JsonMapper.builder().build()
                .readTree(Files.readString(Path.of("data/barbearia.json")))
                .get("agora_nos_testes").asString();
        return Clock.fixed(OffsetDateTime.parse(now).toInstant(), ZoneId.of("America/Sao_Paulo"));
    }
}
