package br.com.jacaranda.agenda.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP de teste: chama a API de verdade (servidor em porta aleatoria). */
public class ApiClient {

    public record Reply(int status, String contentType, JsonNode body) {

        public String code() {
            return body.path("codigo").asString("");
        }
    }

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Reply book(String professionalId, String serviceId, String startsAt, String patientName) {
        return post("/bookings", Map.of(
                "professionalId", professionalId,
                "serviceId", serviceId,
                "startsAt", startsAt,
                "patientName", patientName));
    }

    public Reply cancel(String bookingId) {
        return post("/bookings/" + bookingId + "/cancel", null);
    }

    public Reply get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    }

    public Reply post(String path, Object body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(publisher)
                .build());
    }

    private Reply send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String text = response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            boolean isJson = contentType.contains("json") && text != null && !text.isBlank();
            return new Reply(response.statusCode(), contentType,
                    isJson ? json.readTree(text) : json.createObjectNode());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
