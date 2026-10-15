package com.example.weather;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService service;

    public WeatherController(WeatherService service) {
        this.service = service;
    }

    @GetMapping
    public WeatherReport current(@RequestParam String city) {
        return service.currentWeather(validCity(city));
    }

    @GetMapping("/forecast")
    public ForecastReport forecast(@RequestParam String city, @RequestParam(defaultValue = "5") int days) {
        if (days < 1 || days > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and 16");
        }
        return service.forecast(validCity(city), days);
    }

    private static String validCity(String city) {
        if (city.isBlank() || city.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "city must be 1-100 characters");
        }
        return city.trim();
    }
}
