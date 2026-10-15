# Weather API

A small Spring Boot REST service that returns current weather and multi-day forecasts.
It calls the free [Open-Meteo](https://open-meteo.com/) APIs, so **no API key is needed**.

## Requirements

- JDK 21 or newer
- Maven 3.9+

## Run

```
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.

## Endpoints

### Current weather

`GET /api/weather?city={name}`

```
curl "http://localhost:8080/api/weather?city=London"
```

```json
{
  "city": "London",
  "country": "United Kingdom",
  "latitude": 51.50853,
  "longitude": -0.12574,
  "timezone": "Europe/London",
  "temperatureC": 20.0,
  "feelsLikeC": 19.2,
  "humidityPercent": 66,
  "windSpeedKmh": 13.3,
  "precipitationMm": 0.0,
  "condition": "Partly cloudy",
  "observedAt": "2026-10-15T17:30"
}
```

`observedAt` is local time in `timezone`.

### Forecast

`GET /api/weather/forecast?city={name}&days={n}`

`days` is optional: default 5, allowed range 1-16. Dates are local dates in `timezone`.
`precipitationChancePercent` is `null` when the provider has no probability for that day.

```
curl "http://localhost:8080/api/weather/forecast?city=Tokyo"
curl "http://localhost:8080/api/weather/forecast?city=Paris&days=3"
```

```json
{
  "city": "London",
  "country": "United Kingdom",
  "latitude": 51.50853,
  "longitude": -0.12574,
  "timezone": "Europe/London",
  "days": [
    {
      "date": "2026-10-16",
      "maxTempC": 16.9,
      "minTempC": 10.0,
      "precipitationMm": 8.7,
      "precipitationChancePercent": 96,
      "maxWindSpeedKmh": 16.6,
      "condition": "Rain"
    }
  ]
}
```

### Health

`GET /actuator/health` (plus `/actuator/health/liveness` and `/actuator/health/readiness`).
Only the health endpoints are exposed.

## Errors

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`):

```
curl -i "http://localhost:8080/api/weather?city=zzzzqqqq"
```

```json
{"type":"about:blank","title":"Not Found","status":404,"detail":"City not found: zzzzqqqq","instance":"/api/weather"}
```

| Status | When |
|--------|------|
| 400 | `city` is missing, blank or longer than 100 characters; `days` outside 1-16 or not a number |
| 404 | City not found |
| 429 | Rate limit exceeded (see `Retry-After` header) |
| 502 | Open-Meteo is unavailable, timed out, or returned incomplete or malformed data |

## Resilience

- **Timeouts:** outbound calls fail after 3 s to connect / 5 s to read (`spring.http.client.*`), returning 502.
- **Caching:** city-to-coordinates lookups are cached for 24 h and weather results for 10 min
  (in memory, Caffeine). Errors are never cached. Results can therefore be up to 10 min old.
- **Rate limiting:** 60 requests per minute per client IP on `/api/**` (fixed window, in memory,
  per instance). Behind a reverse proxy set `server.forward-headers-strategy=native` so the real
  client IP is used.
- **Graceful shutdown:** in-flight requests get up to 20 s to finish.

## Configuration

All in `src/main/resources/application.properties`:

| Property | Default | Purpose |
|----------|---------|---------|
| `weather.geocoding-url` / `weather.forecast-url` | Open-Meteo URLs | Provider base URLs |
| `spring.http.client.connect-timeout` / `read-timeout` | `3s` / `5s` | Outbound HTTP timeouts |
| `weather.cache.geocode-ttl` / `weather-ttl` | `24h` / `10m` | Cache lifetimes |
| `weather.cache.max-entries` | `1000` | Max entries per cache |
| `weather.rate-limit.requests-per-minute` | `60` | Per-IP limit |

Override at startup, e.g. `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`.

## Test

```
mvn test
```

Unit tests mock HTTP calls; integration tests run against a local fake of the Open-Meteo APIs,
so the whole suite runs offline.

## Notes

Open-Meteo's free tier is intended for non-commercial use; see their
[terms](https://open-meteo.com/en/terms) before using this in a commercial product.