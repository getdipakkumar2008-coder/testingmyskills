package com.example.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Calls the free Open-Meteo APIs (no API key required). */
@Service
public class WeatherService {

    private final RestClient geocoding;
    private final RestClient forecast;

    public WeatherService(RestClient.Builder builder,
                          @Value("${weather.geocoding-url}") String geocodingUrl,
                          @Value("${weather.forecast-url}") String forecastUrl) {
        this.geocoding = builder.clone().baseUrl(geocodingUrl).build();
        this.forecast = builder.clone().baseUrl(forecastUrl).build();
    }

    public WeatherReport currentWeather(String city) {
        try {
            Place place = findPlace(city);
            Current c = fetchCurrent(place);
            return new WeatherReport(place.name(), place.country(), place.latitude(), place.longitude(),
                    c.temperature(), c.apparentTemperature(), c.humidity(), c.windSpeed(),
                    c.precipitation(), describe(c.weatherCode()), c.time());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider unavailable", e);
        }
    }

    public ForecastReport forecast(String city, int days) {
        try {
            Place place = findPlace(city);
            DailyResponse res = forecast.get()
                    .uri("/v1/forecast?latitude={lat}&longitude={lon}&forecast_days={days}&timezone=auto"
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,"
                            + "precipitation_probability_max,wind_speed_10m_max",
                            place.latitude(), place.longitude(), days)
                    .retrieve().body(DailyResponse.class);
            if (res == null || res.daily() == null || res.daily().time() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from weather provider");
            }
            Daily d = res.daily();
            List<ForecastReport.Day> out = new ArrayList<>();
            for (int i = 0; i < d.time().size(); i++) {
                out.add(new ForecastReport.Day(d.time().get(i), d.maxTemp().get(i), d.minTemp().get(i),
                        d.precipitation().get(i), orZero(d.precipitationChance(), i),
                        d.maxWind().get(i), describe(d.weatherCode().get(i))));
            }
            return new ForecastReport(place.name(), place.country(), place.latitude(), place.longitude(), out);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Weather provider unavailable", e);
        }
    }

    private static int orZero(List<Integer> list, int i) {
        return list == null || list.get(i) == null ? 0 : list.get(i);
    }

    private Place findPlace(String city) {
        GeoResponse res = geocoding.get()
                .uri("/v1/search?name={name}&count=1&language=en&format=json", city)
                .retrieve().body(GeoResponse.class);
        if (res == null || res.results() == null || res.results().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + city);
        }
        return res.results().get(0);
    }

    private Current fetchCurrent(Place p) {
        ForecastResponse res = forecast.get()
                .uri("/v1/forecast?latitude={lat}&longitude={lon}"
                        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,"
                        + "precipitation,weather_code,wind_speed_10m", p.latitude(), p.longitude())
                .retrieve().body(ForecastResponse.class);
        if (res == null || res.current() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from weather provider");
        }
        return res.current();
    }

    /** WMO weather interpretation codes. */
    static String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2, 3 -> "Partly cloudy";
            case 45, 48 -> "Fog";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67 -> "Rain";
            case 71, 73, 75, 77 -> "Snow";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Unknown";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeoResponse(List<Place> results) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Place(String name, String country, double latitude, double longitude) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ForecastResponse(Current current) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Current(String time,
                   @JsonProperty("temperature_2m") double temperature,
                   @JsonProperty("apparent_temperature") double apparentTemperature,
                   @JsonProperty("relative_humidity_2m") int humidity,
                   @JsonProperty("precipitation") double precipitation,
                   @JsonProperty("weather_code") int weatherCode,
                   @JsonProperty("wind_speed_10m") double windSpeed) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DailyResponse(Daily daily) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Daily(List<String> time,
                 @JsonProperty("weather_code") List<Integer> weatherCode,
                 @JsonProperty("temperature_2m_max") List<Double> maxTemp,
                 @JsonProperty("temperature_2m_min") List<Double> minTemp,
                 @JsonProperty("precipitation_sum") List<Double> precipitation,
                 @JsonProperty("precipitation_probability_max") List<Integer> precipitationChance,
                 @JsonProperty("wind_speed_10m_max") List<Double> maxWind) { }
}
