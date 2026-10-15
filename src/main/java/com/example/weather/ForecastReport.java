package com.example.weather;

import java.util.List;

/** Multi-day forecast response of this API. Day dates are local dates in {@code timezone}. */
public record ForecastReport(String city, String country, double latitude, double longitude,
                             String timezone, List<Day> days) {

    /** {@code precipitationChancePercent} is null when the provider has no probability for that day. */
    public record Day(String date,
                      double maxTempC,
                      double minTempC,
                      double precipitationMm,
                      Integer precipitationChancePercent,
                      double maxWindSpeedKmh,
                      String condition) {
    }
}
