package com.example.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class WeatherServiceTest {

    private MockRestServiceServer server;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WeatherService(builder, "http://geo", "http://fc");
    }

    @Test
    void returnsReport() {
        server.expect(requestTo(startsWith("http://geo/v1/search")))
                .andRespond(withSuccess("{\"results\":[{\"name\":\"Paris\",\"country\":\"France\","
                        + "\"latitude\":48.85,\"longitude\":2.35}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("http://fc/v1/forecast")))
                .andRespond(withSuccess("{\"current\":{\"time\":\"2026-10-15T10:00\",\"temperature_2m\":14.2,"
                        + "\"apparent_temperature\":13.0,\"relative_humidity_2m\":70,\"precipitation\":0.0,"
                        + "\"weather_code\":3,\"wind_speed_10m\":9.5}}", MediaType.APPLICATION_JSON));

        WeatherReport r = service.currentWeather("Paris");

        assertThat(r.city()).isEqualTo("Paris");
        assertThat(r.temperatureC()).isEqualTo(14.2);
        assertThat(r.condition()).isEqualTo("Partly cloudy");
    }

    @Test
    void unknownCityIs404() {
        server.expect(requestTo(startsWith("http://geo")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.currentWeather("Nowhereville"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returnsForecastDays() {
        server.expect(requestTo(startsWith("http://geo/v1/search")))
                .andRespond(withSuccess("{\"results\":[{\"name\":\"Paris\",\"country\":\"France\","
                        + "\"latitude\":48.85,\"longitude\":2.35}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("http://fc/v1/forecast")))
                .andRespond(withSuccess("{\"daily\":{\"time\":[\"2026-10-15\",\"2026-10-16\"],"
                        + "\"weather_code\":[0,63],\"temperature_2m_max\":[18.0,15.5],"
                        + "\"temperature_2m_min\":[9.0,8.1],\"precipitation_sum\":[0.0,6.2],"
                        + "\"precipitation_probability_max\":[5,90],\"wind_speed_10m_max\":[12.0,25.4]}}",
                        MediaType.APPLICATION_JSON));

        ForecastReport r = service.forecast("Paris", 2);

        assertThat(r.days()).hasSize(2);
        assertThat(r.days().get(1).condition()).isEqualTo("Rain");
        assertThat(r.days().get(1).precipitationChancePercent()).isEqualTo(90);
        assertThat(r.days().get(0).maxTempC()).isEqualTo(18.0);
    }
}
