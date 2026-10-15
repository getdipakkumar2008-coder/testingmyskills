package com.example.weather;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = WeatherController.class, properties = "spring.mvc.problemdetails.enabled=true")
class WeatherControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WeatherService service;

    @Test
    void currentWeatherTrimsCity() throws Exception {
        when(service.currentWeather("London")).thenReturn(new WeatherReport("London", "United Kingdom", 51.5,
                -0.1, "Europe/London", 20.0, 19.0, 60, 10.0, 0.0, "Overcast", "2026-10-15T10:00"));

        mvc.perform(get("/api/weather").param("city", "  London "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("London"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"));
    }

    @Test
    void blankCityIs400ProblemDetail() throws Exception {
        mvc.perform(get("/api/weather").param("city", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("city must be 1-100 characters"));
        verifyNoInteractions(service);
    }

    @Test
    void tooLongCityIs400() throws Exception {
        mvc.perform(get("/api/weather").param("city", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("city must be 1-100 characters"));
    }

    @Test
    void missingCityIs400() throws Exception {
        mvc.perform(get("/api/weather"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void forecastDefaultsToFiveDays() throws Exception {
        when(service.forecast("Tokyo", 5)).thenReturn(
                new ForecastReport("Tokyo", "Japan", 35.7, 139.7, "Asia/Tokyo", List.of()));

        mvc.perform(get("/api/weather/forecast").param("city", "Tokyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Tokyo"));
        verify(service).forecast("Tokyo", 5);
    }

    @Test
    void forecastDaysOutOfRangeIs400() throws Exception {
        for (String days : new String[] {"0", "17", "-3"}) {
            mvc.perform(get("/api/weather/forecast").param("city", "Tokyo").param("days", days))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("days must be between 1 and 16"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void forecastNonNumericDaysIs400() throws Exception {
        mvc.perform(get("/api/weather/forecast").param("city", "Tokyo").param("days", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forecastBoundaryDaysAccepted() throws Exception {
        when(service.forecast("Tokyo", 16)).thenReturn(
                new ForecastReport("Tokyo", "Japan", 35.7, 139.7, "Asia/Tokyo", List.of()));

        mvc.perform(get("/api/weather/forecast").param("city", "Tokyo").param("days", "16"))
                .andExpect(status().isOk());
    }
}
