package event_planer.project.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import event_planer.project.dto.WeatherData;
import event_planer.project.service.WeatherService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * GET /api/weather?city=London&date=2026-04-15
 * GET /api/weather/range?city=London&startDate=2026-04-15&endDate=2026-04-20
 *
 * Proxies OpenWeatherMap so the Android app never holds the API key.
 * Requires a valid JWT (handled by SecurityConfig).
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@Tag(name = "Weather", description = "Weather forecasts via Open-Meteo (requires auth)")
@SecurityRequirement(name = "bearerAuth")
public class WeatherController {

    private final WeatherService weatherService;

    /** Single-day weather snapshot. */
    @GetMapping
    public ResponseEntity<WeatherData> getWeather(
            @RequestParam String city,
            @RequestParam(required = false, defaultValue = "") String date) {
        WeatherData data = weatherService.getWeatherForCity(city, date);
        return ResponseEntity.ok(data);
    }

    /**
     * Returns one weather entry per calendar day between startDate and endDate (inclusive).
     * The Android app uses this to show a range of forecasts so the user can pick
     * the best day for their event before creating it.
     */
    @GetMapping("/range")
    public ResponseEntity<List<WeatherData>> getWeatherRange(
            @RequestParam String city,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        List<WeatherData> data = weatherService.getWeatherForRange(city, startDate, endDate);
        return ResponseEntity.ok(data);
    }
}
