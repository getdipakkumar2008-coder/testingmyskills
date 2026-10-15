package com.example.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "weather.rate-limit.requests-per-minute=3")
class RateLimitTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void blocksAfterLimitWithRetryAfterAndLeavesActuatorAlone() {
        // blank city -> 400 from the controller without touching the upstream provider
        for (int i = 0; i < 3; i++) {
            assertThat(rest.getForEntity("/api/weather?city=", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<String> limited = rest.getForEntity("/api/weather?city=", String.class);

        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(limited.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(limited.getBody()).contains("Rate limit exceeded");
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}