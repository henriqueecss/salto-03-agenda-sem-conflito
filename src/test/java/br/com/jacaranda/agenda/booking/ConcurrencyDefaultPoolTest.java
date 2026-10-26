package br.com.jacaranda.agenda.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import br.com.jacaranda.agenda.TestcontainersConfiguration;
import br.com.jacaranda.agenda.support.ApiClient;
import br.com.jacaranda.agenda.support.ApiClient.Reply;
import br.com.jacaranda.agenda.support.FixedClockConfig;
import br.com.jacaranda.agenda.support.Parallel;

/**
 * Mesmo pedido simultaneo, mas com o pool de conexoes padrao (10): mais pedidos que conexoes nao pode
 * derrubar a API. Um pedido nunca deve ficar esperando uma segunda conexao enquanto segura a primeira.
 */
@Import({TestcontainersConfiguration.class, FixedClockConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyDefaultPoolTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM booking").update();
    }

    @Test
    void moreRequestsThanPooledConnectionsStillCreateExactlyOneBooking() throws Exception {
        ApiClient api = new ApiClient(port);

        List<Reply> replies = Parallel.fireTogether(50, i ->
                () -> api.book("PRO2", "S1", "2026-11-05T15:00", "Paciente " + i));

        assertThat(replies.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(replies.stream().filter(r -> r.status() == 409 && r.code().equals("CONFLITO")).count())
                .isEqualTo(49);
    }
}
