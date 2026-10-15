package com.example.weather;

/** Public response of this API. {@code observedAt} is local time in {@code timezone}. */
public record WeatherReport(
        String city,
        String country,
        double latitude,
        double longitude,
        String timezone,
        double temperatureC,
        double feelsLikeC,
        int humidityPercent,
        double windSpeedKmh,
        double precipitationMm,
        String condition,
        String observedAt) {
}
