package event_planer.project.controller;

import event_planer.project.dto.WeatherData;
import event_planer.project.service.WeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

    @Mock private WeatherService weatherService;
    @InjectMocks private WeatherController weatherController;

    @Test
    void getWeatherDelegatesToService() {
        WeatherData data = new WeatherData("Berlin", "2025-07-01", 28.5, 20, "Clear sky", "");
        when(weatherService.getWeatherForCity("Berlin", "2025-07-01")).thenReturn(data);

        ResponseEntity<WeatherData> response = weatherController.getWeather("Berlin", "2025-07-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTemperature()).isEqualTo(28.5);
    }

    @Test
    void getWeatherRangeDelegatesToService() {
        List<WeatherData> data = List.of(
                new WeatherData("Berlin", "2025-07-01", 28.5, 20, "Clear sky", ""),
                new WeatherData("Berlin", "2025-07-02", 25.0, 40, "Partly cloudy", "")
        );
        when(weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-02")).thenReturn(data);

        ResponseEntity<List<WeatherData>> response =
                weatherController.getWeatherRange("Berlin", "2025-07-01", "2025-07-02");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
