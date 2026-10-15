package com.example.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Calls the free Open-Meteo forecast API (no API key required). */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final GeocodingService geocoding;
    private final RestClient forecast;

    public WeatherService(GeocodingService geocoding,
                          RestClient.Builder builder,
                          @Value("${weather.forecast-url}") String forecastUrl) {
        this.geocoding = geocoding;
        this.forecast = builder.clone().baseUrl(forecastUrl).build();
    }

    @Cacheable(cacheNames = "current", key = "#city.toLowerCase(T(java.util.Locale).ROOT)")
    public WeatherReport currentWeather(String city) {
        try {
            GeocodingService.Place place = geocoding.find(city);
            CurrentResponse res = forecast.get()
                    .uri("/v1/forecast?latitude={lat}&longitude={lon}&timezone=auto"
                            + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
                            + "precipitation,weather_code,wind_speed_10m", place.latitude(), place.longitude())
                    .retrieve().body(CurrentResponse.class);
            if (res == null || res.current() == null) {
                throw incomplete();
            }
            Current c = res.current();
            return new WeatherReport(place.name(), place.country(), place.latitude(), place.longitude(),
                    res.timezone(), required(c.temperature()), required(c.apparentTemperature()),
                    required(c.humidity()), required(c.windSpeed()), required(c.precipitation()),
                    describe(required(c.weatherCode())), required(c.time()));
        } catch (RestClientException e) {
            throw providerUnavailable(e);
        }
    }

    @Cacheable(cacheNames = "forecast", key = "#city.toLowerCase(T(java.util.Locale).ROOT) + ':' + #days")
    public ForecastReport forecast(String city, int days) {
        try {
            GeocodingService.Place place = geocoding.find(city);
            DailyResponse res = forecast.get()
                    .uri("/v1/forecast?latitude={lat}&longitude={lon}&forecast_days={days}&timezone=auto"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                            + "precipitation_probability_max,wind_speed_10m_max",
                            place.latitude(), place.longitude(), days)
                    .retrieve().body(DailyResponse.class);
            if (res == null || res.daily() == null || res.daily().time() == null || res.daily().time().isEmpty()) {
                throw incomplete();
            }
            Daily d = res.daily();
            List<ForecastReport.Day> out = new ArrayList<>();
            for (int i = 0; i < d.time().size(); i++) {
                out.add(new ForecastReport.Day(
                        required(d.time().get(i)), at(d.maxTemp(), i), at(d.minTemp(), i),
                        at(d.precipitation(), i), optionalAt(d.precipitationChance(), i),
                        at(d.maxWind(), i), describe(at(d.weatherCode(), i))));
            }
            return new ForecastReport(place.name(), place.country(), place.latitude(), place.longitude(),
                    res.timezone(), out);
        } catch (RestClientException e) {
            throw providerUnavailable(e);
        }
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw incomplete();
        }
        return value;
    }

    private static <T> T at(List<T> list, int i) {
        if (list == null || i >= list.size()) {
            throw incomplete();
        }
        return required(list.get(i));
    }

    private static <T> T optionalAt(List<T> list, int i) {
        return list == null || i >= list.size() ? null : list.get(i);
    }

    private static ResponseStatusException incomplete() {
        log.warn("Weather provider returned incomplete data");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Incomplete data from weather provider");
    }

    private static ResponseStatusException providerUnavailable(RestClientException e) {
        log.warn("Weather provider call failed: {}", e.toString());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider unavailable", e);
    }

    /** WMO weather interpretation codes. */
    static String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snow";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CurrentResponse(String timezone, Current current) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Current(String time,
                   @JsonProperty("temperature_2m") Double temperature,
                   @JsonProperty("apparent_temperature") Double apparentTemperature,
                   @JsonProperty("relative_humidity_2m") Integer humidity,
                   @JsonProperty("precipitation") Double precipitation,
                   @JsonProperty("weather_code") Integer weatherCode,
                   @JsonProperty("wind_speed_10m") Double windSpeed) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DailyResponse(String timezone, Daily daily) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Daily(List<String> time,
                 @JsonProperty("weather_code") List<Integer> weatherCode,
                 @JsonProperty("temperature_2m_max") List<Double> maxTemp,
                 @JsonProperty("temperature_2m_min") List<Double> minTemp,
                 @JsonProperty("precipitation_sum") List<Double> precipitation,
                 @JsonProperty("precipitation_probability_max") List<Integer> precipitationChance,
                 @JsonProperty("wind_speed_10m_max") List<Double> maxWind) { }
}
