package com.example.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class WeatherServiceTest {

    private static final String PARIS = "{\"results\":[{\"name\":\"Paris\",\"country\":\"France\","
            + "\"latitude\":48.85,\"longitude\":2.35}]}";

    private MockRestServiceServer server;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WeatherService(new GeocodingService(builder, "http://geo"), builder, "http://fc");
    }

    private void geocodeParis() {
        server.expect(requestTo(startsWith("http://geo/v1/search")))
                .andRespond(withSuccess(PARIS, MediaType.APPLICATION_JSON));
    }

    private void forecastReturns(String json) {
        server.expect(requestTo(startsWith("http://fc/v1/forecast")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private static String daily(String body) {
        return "{\"timezone\":\"Europe/Paris\",\"daily\":{" + body + "}}";
    }

    private void assertBadGateway(Runnable call, String detail) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getReason()).isEqualTo(detail);
                });
    }

    @Test
    void returnsReportWithTimezone() {
        geocodeParis();
        server.expect(requestTo(containsString("timezone=auto")))
                .andRespond(withSuccess("{\"timezone\":\"Europe/Paris\",\"current\":{\"time\":\"2026-10-15T10:00\","
                        + "\"temperature_2m\":14.2,\"apparent_temperature\":13.0,\"relative_humidity_2m\":70,"
                        + "\"precipitation\":0.0,\"weather_code\":3,\"wind_speed_10m\":9.5}}",
                        MediaType.APPLICATION_JSON));

        WeatherReport r = service.currentWeather("Paris");

        assertThat(r.city()).isEqualTo("Paris");
        assertThat(r.timezone()).isEqualTo("Europe/Paris");
        assertThat(r.temperatureC()).isEqualTo(14.2);
        assertThat(r.condition()).isEqualTo("Overcast");
        server.verify();
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
    void providerServerErrorIs502() {
        geocodeParis();
        server.expect(requestTo(startsWith("http://fc"))).andRespond(withServerError());

        assertBadGateway(() -> service.currentWeather("Paris"), "Weather provider unavailable");
    }

    @Test
    void geocodingServerErrorIs502() {
        server.expect(requestTo(startsWith("http://geo"))).andRespond(withServerError());

        assertBadGateway(() -> service.currentWeather("Paris"), "Weather provider unavailable");
    }

    @Test
    void malformedJsonIs502() {
        geocodeParis();
        forecastReturns("{not json");

        assertBadGateway(() -> service.currentWeather("Paris"), "Weather provider unavailable");
    }

    @Test
    void missingCurrentBlockIs502() {
        geocodeParis();
        forecastReturns("{}");

        assertBadGateway(() -> service.currentWeather("Paris"), "Incomplete data from weather provider");
    }

    @Test
    void missingCurrentFieldIs502NotZero() {
        geocodeParis();
        forecastReturns("{\"current\":{\"time\":\"2026-10-15T10:00\",\"apparent_temperature\":13.0,"
                + "\"relative_humidity_2m\":70,\"precipitation\":0.0,\"weather_code\":3,\"wind_speed_10m\":9.5}}");

        assertBadGateway(() -> service.currentWeather("Paris"), "Incomplete data from weather provider");
    }

    @Test
    void returnsForecastDays() {
        geocodeParis();
        server.expect(requestTo(containsString("forecast_days=2")))
                .andRespond(withSuccess(daily("\"time\":[\"2026-10-15\",\"2026-10-16\"],"
                        + "\"weather_code\":[0,63],\"temperature_2m_max\":[18.0,15.5],"
                        + "\"temperature_2m_min\":[9.0,8.1],\"precipitation_sum\":[0.0,6.2],"
                        + "\"precipitation_probability_max\":[5,90],\"wind_speed_10m_max\":[12.0,25.4]"),
                        MediaType.APPLICATION_JSON));

        ForecastReport r = service.forecast("Paris", 2);

        assertThat(r.timezone()).isEqualTo("Europe/Paris");
        assertThat(r.days()).hasSize(2);
        assertThat(r.days().get(1).condition()).isEqualTo("Rain");
        assertThat(r.days().get(1).precipitationChancePercent()).isEqualTo(90);
        assertThat(r.days().get(0).maxTempC()).isEqualTo(18.0);
        server.verify();
    }

    @Test
    void missingPrecipitationChanceIsNullNotZero() {
        geocodeParis();
        forecastReturns(daily("\"time\":[\"2026-10-15\"],\"weather_code\":[0],\"temperature_2m_max\":[18.0],"
                + "\"temperature_2m_min\":[9.0],\"precipitation_sum\":[0.0],\"wind_speed_10m_max\":[12.0]"));

        assertThat(service.forecast("Paris", 1).days().get(0).precipitationChancePercent()).isNull();
    }

    @Test
    void nullEntryInDailyListIs502() {
        geocodeParis();
        forecastReturns(daily("\"time\":[\"2026-10-15\"],\"weather_code\":[0],\"temperature_2m_max\":[null],"
                + "\"temperature_2m_min\":[9.0],\"precipitation_sum\":[0.0],\"wind_speed_10m_max\":[12.0]"));

        assertBadGateway(() -> service.forecast("Paris", 1), "Incomplete data from weather provider");
    }

    @Test
    void shortDailyListIs502() {
        geocodeParis();
        forecastReturns(daily("\"time\":[\"2026-10-15\",\"2026-10-16\"],\"weather_code\":[0,1],"
                + "\"temperature_2m_max\":[18.0],\"temperature_2m_min\":[9.0,8.0],"
                + "\"precipitation_sum\":[0.0,0.0],\"wind_speed_10m_max\":[12.0,10.0]"));

        assertBadGateway(() -> service.forecast("Paris", 2), "Incomplete data from weather provider");
    }

    @Test
    void emptyDailyIs502() {
        geocodeParis();
        forecastReturns(daily("\"time\":[]"));

        assertBadGateway(() -> service.forecast("Paris", 1), "Incomplete data from weather provider");
    }

    @ParameterizedTest
    @CsvSource({"0,Clear sky", "1,Mainly clear", "2,Partly cloudy", "3,Overcast", "45,Fog", "53,Drizzle",
            "56,Freezing drizzle", "63,Rain", "67,Freezing rain", "75,Snow", "77,Snow grains",
            "81,Rain showers", "86,Snow showers", "95,Thunderstorm", "99,Thunderstorm with hail",
            "42,Unknown"})
    void describesWmoCodes(int code, String expected) {
        assertThat(WeatherService.describe(code)).isEqualTo(expected);
    }
}