package br.com.jacaranda.agenda.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import br.com.jacaranda.agenda.TestcontainersConfiguration;
import br.com.jacaranda.agenda.error.ErrorCode;
import br.com.jacaranda.agenda.support.ApiClient;
import br.com.jacaranda.agenda.support.ApiClient.Reply;
import br.com.jacaranda.agenda.support.FixedClockConfig;

/** Teste de aceitacao: os 26 passos de data/cenarios.csv, em ordem, sobre banco limpo, pela API real. */
@Import({TestcontainersConfiguration.class, FixedClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsvScenariosTest {

    private record Step(int number, String action, String ref, String professional, String service,
            String start, String expected, String note) {
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @TestFactory
    Stream<DynamicTest> runsAllScenarios() throws IOException {
        jdbc.sql("DELETE FROM booking").update();
        ApiClient api = new ApiClient(port);
        Map<String, String> bookingIdByRef = new HashMap<>();

        return readSteps().stream().map(step ->
                DynamicTest.dynamicTest("passo %d: %s".formatted(step.number(), step.note()),
                        () -> run(step, api, bookingIdByRef)));
    }

    private void run(Step step, ApiClient api, Map<String, String> bookingIdByRef) {
        Reply reply = switch (step.action()) {
            case "reservar" -> api.book(step.professional(), step.service(), step.start(), "Paciente " + step.number());
            case "cancelar" -> api.cancel(bookingIdByRef.get(step.ref()));
            default -> throw new IllegalArgumentException("Acao desconhecida: " + step.action());
        };

        switch (step.expected()) {
            case "criada" -> {
                assertThat(reply.status()).as(reply.body().toString()).isEqualTo(201);
                assertThat(reply.body().path("status").asString()).isEqualTo("ACTIVE");
                bookingIdByRef.put(step.ref(), reply.body().path("id").asString());
            }
            case "cancelada" -> {
                assertThat(reply.status()).as(reply.body().toString()).isEqualTo(200);
                assertThat(reply.body().path("status").asString()).isEqualTo("CANCELLED");
            }
            default -> {
                ErrorCode code = ErrorCode.valueOf(step.expected());
                assertThat(reply.status()).as(reply.body().toString()).isEqualTo(code.status().value());
                assertThat(reply.contentType()).startsWith("application/problem+json");
                assertThat(reply.code()).isEqualTo(code.name());
            }
        }
    }

    private static List<Step> readSteps() throws IOException {
        return Files.readAllLines(Path.of("data/cenarios.csv")).stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(";", -1))
                .map(c -> new Step(Integer.parseInt(c[0]), c[1], c[2], c[3], c[4], c[5], c[6], c[7]))
                .toList();
    }
}
