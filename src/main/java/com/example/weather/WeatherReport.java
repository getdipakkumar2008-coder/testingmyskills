package com.example.weather;

/** Public response of this API. */
public record WeatherReport(
        String city,
        String country,
        double latitude,
        double longitude,
        double temperatureC,
        double feelsLikeC,
        int humidityPercent,
        double windSpeedKmh,
        double precipitationMm,
        String condition,
        String observedAt) {
}
