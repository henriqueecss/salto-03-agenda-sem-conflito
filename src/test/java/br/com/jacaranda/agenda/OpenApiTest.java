package br.com.jacaranda.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import br.com.jacaranda.agenda.support.ApiClient;
import br.com.jacaranda.agenda.support.ApiClient.Reply;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiTest {

    @LocalServerPort
    int port;

    @Test
    void publishesTheContractWithEveryEndpoint() {
        Reply docs = new ApiClient(port).get("/v3/api-docs");

        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.body().path("paths").propertyNames())
                .contains("/bookings", "/bookings/{id}", "/bookings/{id}/cancel", "/availability",
                        "/professionals", "/services");
    }

    @Test
    void servesSwaggerUi() {
        Reply ui = new ApiClient(port).get("/swagger-ui/index.html");

        assertThat(ui.status()).isEqualTo(200);
    }
}
