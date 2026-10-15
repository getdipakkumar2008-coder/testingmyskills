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
  "temperatureC": 20.0,
  "feelsLikeC": 19.2,
  "humidityPercent": 66,
  "windSpeedKmh": 13.3,
  "precipitationMm": 0.0,
  "condition": "Partly cloudy",
  "observedAt": "2026-10-15T16:30"
}
```

### Forecast

`GET /api/weather/forecast?city={name}&days={n}`

`days` is optional: default 5, allowed range 1-16.

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

## Errors

| Status | When |
|--------|------|
| 400 | `city` is blank or longer than 100 characters; `days` outside 1-16; `city` missing |
| 404 | City not found |
| 502 | Open-Meteo is unavailable or returned an empty response |

Error bodies use Spring's standard JSON format, e.g.:

```
curl -i "http://localhost:8080/api/weather?city=zzzzqqqq"
```

## Configuration

Provider base URLs are in `src/main/resources/application.properties`:

```
weather.geocoding-url=https://geocoding-api.open-meteo.com
weather.forecast-url=https://api.open-meteo.com
```

Override any of them at startup, e.g. `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090`.

## Test

```
mvn test
```

HTTP calls to Open-Meteo are mocked, so tests run offline.

## Notes

Open-Meteo's free tier is intended for non-commercial use; see their
[terms](https://open-meteo.com/en/terms) before using this in a commercial product.
