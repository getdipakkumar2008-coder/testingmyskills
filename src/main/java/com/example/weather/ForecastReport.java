package com.example.weather;

import java.util.List;

/** Multi-day forecast response of this API. */
public record ForecastReport(String city, String country, double latitude, double longitude, List<Day> days) {

    public record Day(String date,
                      double maxTempC,
                      double minTempC,
                      double precipitationMm,
                      int precipitationChancePercent,
                      double maxWindSpeedKmh,
                      String condition) {
    }
}
