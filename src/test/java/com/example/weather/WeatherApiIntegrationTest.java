package com.example.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Full-stack tests against a local fake of the Open-Meteo APIs (real HTTP, real timeouts, real caching). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.http.client.connect-timeout=500ms",
        "spring.http.client.read-timeout=500ms",
        "weather.rate-limit.requests-per-minute=1000"})
class WeatherApiIntegrationTest {

    private static final AtomicInteger geocodeHits = new AtomicInteger();
    private static final AtomicInteger forecastHits = new AtomicInteger();
    private static HttpServer provider;
    private static ExecutorService executor;

    @BeforeAll
    static void startProvider() throws IOException {
        executor = Executors.newFixedThreadPool(4);
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.setExecutor(executor);
        provider.createContext("/v1/search", ex -> {
            geocodeHits.incrementAndGet();
            String query = ex.getRequestURI().getQuery();
            try {
                if (query.contains("name=slow")) {
                    Thread.sleep(2000);
                }
                if (query.contains("name=unknown")) {
                    reply(ex, 200, "{}");
                } else if (query.contains("name=broken")) {
                    reply(ex, 200, "{\"results\":[{\"name\":\"Broken\",\"country\":\"X\","
                            + "\"latitude\":99.0,\"longitude\":0.0}]}");
                } else {
                    reply(ex, 200, "{\"results\":[{\"name\":\"Paris\",\"country\":\"France\","
                            + "\"latitude\":48.85,\"longitude\":2.35}]}");
                }
            } catch (Exception ignored) {
                // client gave up (timeout test)
            }
        });
        provider.createContext("/v1/forecast", ex -> {
            forecastHits.incrementAndGet();
            String query = ex.getRequestURI().getQuery();
            if (query.contains("latitude=99.0")) {
                reply(ex, 500, "{}");
            } else if (query.contains("daily=")) {
                reply(ex, 200, "{\"timezone\":\"Europe/Paris\",\"daily\":{\"time\":[\"2026-10-15\"],"
                        + "\"weather_code\":[0],\"temperature_2m_max\":[18.0],\"temperature_2m_min\":[9.0],"
                        + "\"precipitation_sum\":[0.0],\"precipitation_probability_max\":[5],"
                        + "\"wind_speed_10m_max\":[12.0]}}");
            } else {
                reply(ex, 200, "{\"timezone\":\"Europe/Paris\",\"current\":{\"time\":\"2026-10-15T10:00\","
                        + "\"temperature_2m\":14.2,\"apparent_temperature\":13.0,\"relative_humidity_2m\":70,"
                        + "\"precipitation\":0.0,\"weather_code\":3,\"wind_speed_10m\":9.5}}");
            }
        });
        provider.start();
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
        executor.shutdownNow();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) throws IOException {
        // the server is started in @BeforeAll, which runs after this; use a fixed holder via lazy suppliers
        registry.add("weather.geocoding-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("weather.forecast-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
    }

    @Autowired
    TestRestTemplate rest;

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private JsonNode body(ResponseEntity<String> res) throws Exception {
        return json.readTree(res.getBody());
    }

    @Test
    void repeatedRequestsAreServedFromCache() {
        int geoBefore = geocodeHits.get();
        int fcBefore = forecastHits.get();

        assertThat(rest.getForEntity("/api/weather?city=Cached", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/weather?city=cached", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/weather?city=CACHED", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(geocodeHits.get() - geoBefore).isEqualTo(1);
        assertThat(forecastHits.get() - fcBefore).isEqualTo(1);
    }

    @Test
    void forecastReusesCachedGeocoding() throws Exception {
        rest.getForEntity("/api/weather?city=Shared", String.class);
        int geoBefore = geocodeHits.get();

        ResponseEntity<String> res = rest.getForEntity("/api/weather/forecast?city=Shared&days=1", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(res).at("/days/0/condition").asText()).isEqualTo("Clear sky");
        assertThat(geocodeHits.get() - geoBefore).isZero();
    }

    @Test
    void unknownCityIs404ProblemDetail() throws Exception {
        ResponseEntity<String> res = rest.getForEntity("/api/weather?city=unknownville", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(body(res).get("detail").asText()).isEqualTo("City not found: unknownville");
    }

    @Test
    void slowProviderTimesOutWith502() throws Exception {
        // plain JDK client: the injected TestRestTemplate shares the 500ms read timeout under test
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest
                .newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/api/weather?city=slowtown")).build();
        long start = System.nanoTime();

        java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(res.statusCode()).isEqualTo(502);
        assertThat(json.readTree(res.body()).get("detail").asText()).isEqualTo("Weather provider unavailable");
        assertThat(ms).as("fails at the 500ms read timeout, not after the 2000ms provider delay").isLessThan(1800);
    }

    @Test
    void providerErrorIs502AndNotCached() throws Exception {
        rest.getForEntity("/api/weather?city=brokenton", String.class);
        int fcBefore = forecastHits.get();

        ResponseEntity<String> res = rest.getForEntity("/api/weather?city=brokenton", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(forecastHits.get() - fcBefore).isEqualTo(1);
    }

    @Test
    void healthEndpointIsUpWithProbes() throws Exception {
        assertThat(body(rest.getForEntity("/actuator/health", String.class)).get("status").asText())
                .isEqualTo("UP");
        assertThat(rest.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}