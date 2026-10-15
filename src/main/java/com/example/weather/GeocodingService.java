package com.example.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Resolves a city name to coordinates using the Open-Meteo geocoding API (results are cached). */
@Service
public class GeocodingService {

    private final RestClient client;

    public GeocodingService(RestClient.Builder builder, @Value("${weather.geocoding-url}") String geocodingUrl) {
        this.client = builder.clone().baseUrl(geocodingUrl).build();
    }

    @Cacheable(cacheNames = "geocode", key = "#city.toLowerCase(T(java.util.Locale).ROOT)")
    public Place find(String city) {
        GeoResponse res = client.get()
                .uri("/v1/search?name={name}&count=1&language=en&format=json", city)
                .retrieve().body(GeoResponse.class);
        if (res == null || res.results() == null || res.results().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + city);
        }
        return res.results().get(0);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeoResponse(List<Place> results) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(String name, String country, double latitude, double longitude) { }
}
