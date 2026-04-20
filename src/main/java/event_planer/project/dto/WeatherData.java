package event_planer.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Weather snapshot returned to the Android app.
 * The backend proxies OpenWeatherMap so the API key never leaves the server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {
    private String city;
    private String date;         // "yyyy-MM-dd" — the event date queried
    private double temperature;  // Celsius
    private int humidity;        // Percentage
    private String description;  // e.g. "overcast clouds"
    private String icon;         // OWM icon code, e.g. "04d"
}
