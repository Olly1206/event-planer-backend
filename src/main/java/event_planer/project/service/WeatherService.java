package event_planer.project.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import event_planer.project.dto.WeatherData;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Fetches weather forecasts from Open-Meteo (https://open-meteo.com/).
 *
 * Open-Meteo is completely free, requires no API key, and supports forecasts
 * up to 16 days ahead — solving the 5-day limit of OWM's free tier.
 *
 * Flow:
 *   1. Geocode the city name via Open-Meteo's Geocoding API (lat/lon).
 *   2. Fetch daily forecast for the requested date range.
 *   3. Map WMO weather codes to human-readable descriptions.
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final RestTemplate restTemplate;

    private static final String GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search";

    private static final String FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast";

    /** WMO weather interpretation codes → readable descriptions. */
    private static final Map<Integer, String> WEATHER_CODES = Map.ofEntries(
            Map.entry(0,  "Clear sky"),
            Map.entry(1,  "Mainly clear"),
            Map.entry(2,  "Partly cloudy"),
            Map.entry(3,  "Overcast"),
            Map.entry(45, "Fog"),
            Map.entry(48, "Icy fog"),
            Map.entry(51, "Light drizzle"),
            Map.entry(53, "Moderate drizzle"),
            Map.entry(55, "Dense drizzle"),
            Map.entry(61, "Slight rain"),
            Map.entry(63, "Moderate rain"),
            Map.entry(65, "Heavy rain"),
            Map.entry(71, "Slight snow"),
            Map.entry(73, "Moderate snow"),
            Map.entry(75, "Heavy snow"),
            Map.entry(77, "Snow grains"),
            Map.entry(80, "Slight showers"),
            Map.entry(81, "Moderate showers"),
            Map.entry(82, "Heavy showers"),
            Map.entry(85, "Slight snow showers"),
            Map.entry(86, "Heavy snow showers"),
            Map.entry(95, "Thunderstorm"),
            Map.entry(96, "Thunderstorm with hail"),
            Map.entry(99, "Thunderstorm with heavy hail")
    );

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns a single-day forecast for {@code date}.
     * Delegates to {@link #getWeatherForRange} so only one code path exists.
     */
    public WeatherData getWeatherForCity(String city, String date) {
        List<WeatherData> range = getWeatherForRange(city, date, date);
        return range.isEmpty()
                ? new WeatherData(city, date, 0, 0, "No data available", "")
                : range.get(0);
    }

    /**
     * Returns one weather snapshot per calendar day in [{@code startDate}, {@code endDate}].
     *
     * Open-Meteo supports up to 16 days ahead at no cost and with no API key.
     *
     * @param city      City name, e.g. "Berlin"
     * @param startDate "yyyy-MM-dd"
     * @param endDate   "yyyy-MM-dd"
     */
    public List<WeatherData> getWeatherForRange(String city, String startDate, String endDate) {
        LocalDate parsedStartDate = parseDate(startDate, "startDate");
        LocalDate parsedEndDate = parseDate(endDate, "endDate");
        validateDateRange(parsedStartDate, parsedEndDate);

        // Step 1: geocode city name → latitude / longitude
        String geocodeUrl = GEOCODING_URL + "?name=" + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&count=1&language=en&format=json";
        GeocodingResponse geo;
        try {
            geo = restTemplate.getForObject(geocodeUrl, GeocodingResponse.class);
        } catch (Exception e) {
            return List.of();
        }

        if (geo == null || geo.results == null || geo.results.isEmpty()) {
            return List.of();
        }

        double lat = geo.results.get(0).latitude;
        double lon = geo.results.get(0).longitude;

        // Step 2: fetch daily forecast for the requested window
        String forecastUrl = FORECAST_URL
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&daily=temperature_2m_max,precipitation_probability_max,weather_code"
                + "&timezone=auto"
                + "&start_date=" + parsedStartDate
                + "&end_date=" + parsedEndDate;

        ForecastResponse forecast;
        try {
            forecast = restTemplate.getForObject(forecastUrl, ForecastResponse.class);
        } catch (Exception e) {
            return List.of();
        }
        if (forecast == null || forecast.daily == null || forecast.daily.time == null) {
            return List.of();
        }

        List<WeatherData> results = new ArrayList<>();
        int size = forecast.daily.time.size();
        for (int i = 0; i < size; i++) {
            String  day    = forecast.daily.time.get(i);
            double  temp   = safeDouble(forecast.daily.temperature2mMax, i);
            int     precip = safeInt(forecast.daily.precipitationProbabilityMax, i);
            int     code   = safeInt(forecast.daily.weathercode, i);
            String  desc   = WEATHER_CODES.getOrDefault(code, "Partly cloudy");
            results.add(new WeatherData(city, day, temp, precip, desc, ""));
        }
        return results;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private double safeDouble(List<Double> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i) : 0.0;
    }

    private int safeInt(List<Integer> list, int i) {
        return (list != null && i < list.size() && list.get(i) != null) ? list.get(i) : 0;
    }

    private LocalDate parseDate(String rawDate, String fieldName) {
        if (rawDate == null || rawDate.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days > 15) {
            throw new IllegalArgumentException("Date range must be 16 days or less");
        }
    }

    // ─── Internal DTOs for Open-Meteo ────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GeocodingResponse {
        List<GeoResult> results;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class GeoResult {
            double latitude;
            double longitude;
            String name;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ForecastResponse {
        DailyData daily;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class DailyData {
            List<String>  time;

            @JsonProperty("temperature_2m_max")
            List<Double>  temperature2mMax;

            @JsonProperty("precipitation_probability_max")
            List<Integer> precipitationProbabilityMax;

            @JsonProperty("weather_code")
            List<Integer> weathercode;
        }
    }
}
