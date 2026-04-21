package event_planer.project.service;

import event_planer.project.dto.WeatherData;
import event_planer.project.service.WeatherService.ForecastResponse;
import event_planer.project.service.WeatherService.GeocodingResponse;
import event_planer.project.service.WeatherService.GeocodingResponse.GeoResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock private RestTemplate restTemplate;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherService(restTemplate);
    }

    // ── Helpers to build Open-Meteo response stubs ──────────────────────────

    private GeocodingResponse geoResponse(double lat, double lon) {
        GeoResult result = new GeoResult();
        result.setLatitude(lat);
        result.setLongitude(lon);
        result.setName("Berlin");
        GeocodingResponse geo = new GeocodingResponse();
        geo.setResults(List.of(result));
        return geo;
    }

    private ForecastResponse forecastResponse(List<String> dates,
                                              List<Double> temps,
                                              List<Integer> precip,
                                              List<Integer> codes) {
        ForecastResponse.DailyData daily = new ForecastResponse.DailyData();
        daily.setTime(dates);
        daily.setTemperature2mMax(temps);
        daily.setPrecipitationProbabilityMax(precip);
        daily.setWeathercode(codes);
        ForecastResponse forecast = new ForecastResponse();
        forecast.setDaily(daily);
        return forecast;
    }

    // ── getWeatherForRange ──────────────────────────────────────────────────

    @Nested
    class GetWeatherForRange {

        @Test
        void returnsEmptyListWhenGeocodingReturnsNull() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(null);

            List<WeatherData> result = weatherService.getWeatherForRange("Nowhere", "2025-01-01", "2025-01-01");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyListWhenGeocodingResultsAreEmpty() {
            GeocodingResponse emptyGeo = new GeocodingResponse();
            emptyGeo.setResults(List.of());
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(emptyGeo);

            List<WeatherData> result = weatherService.getWeatherForRange("Nowhere", "2025-01-01", "2025-01-01");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyListWhenGeocodingThrows() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenThrow(new RuntimeException("API down"));

            List<WeatherData> result = weatherService.getWeatherForRange("Nowhere", "2025-01-01", "2025-01-01");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyListWhenForecastIsNull() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(null);

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-01-01", "2025-01-01");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyListWhenForecastThrows() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenThrow(new RuntimeException("API down"));

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-01-01", "2025-01-01");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsSingleDayForecast() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(forecastResponse(
                            List.of("2025-07-01"),
                            List.of(28.5),
                            List.of(20),
                            List.of(0)
                    ));

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-01");

            assertThat(result).hasSize(1);
            WeatherData day = result.get(0);
            assertThat(day.getCity()).isEqualTo("Berlin");
            assertThat(day.getDate()).isEqualTo("2025-07-01");
            assertThat(day.getTemperature()).isEqualTo(28.5);
            assertThat(day.getHumidity()).isEqualTo(20);
            assertThat(day.getDescription()).isEqualTo("Clear sky");
        }

        @Test
        void returnsMultiDayForecast() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(forecastResponse(
                            List.of("2025-07-01", "2025-07-02", "2025-07-03"),
                            List.of(28.5, 30.0, 22.0),
                            List.of(20, 5, 80),
                            List.of(0, 1, 65)
                    ));

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-03");

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getDescription()).isEqualTo("Clear sky");
            assertThat(result.get(1).getDescription()).isEqualTo("Mainly clear");
            assertThat(result.get(2).getDescription()).isEqualTo("Heavy rain");
        }

        @Test
        void unknownWeatherCodeFallsBackToPartlyCloudy() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(forecastResponse(
                            List.of("2025-07-01"),
                            List.of(20.0),
                            List.of(50),
                            List.of(999) // unknown code
                    ));

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-01");

            assertThat(result.get(0).getDescription()).isEqualTo("Partly cloudy");
        }

        @Test
        void handlesNullListsInDailyData() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));

            ForecastResponse.DailyData daily = new ForecastResponse.DailyData();
            daily.setTime(List.of("2025-07-01"));
            daily.setTemperature2mMax(null);           // null temp list
            daily.setPrecipitationProbabilityMax(null); // null precip list
            daily.setWeathercode(null);                // null code list
            ForecastResponse forecast = new ForecastResponse();
            forecast.setDaily(daily);

            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(forecast);

            List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-01");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTemperature()).isEqualTo(0.0);
            assertThat(result.get(0).getHumidity()).isEqualTo(0);
            assertThat(result.get(0).getDescription()).isEqualTo("Clear sky"); // code 0
        }
    }

    // ── getWeatherForCity ───────────────────────────────────────────────────

    @Nested
    class GetWeatherForCity {

        @Test
        void delegatesToGetWeatherForRange() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));
            when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                    .thenReturn(forecastResponse(
                            List.of("2025-07-01"),
                            List.of(25.0),
                            List.of(10),
                            List.of(2)
                    ));

            WeatherData result = weatherService.getWeatherForCity("Berlin", "2025-07-01");

            assertThat(result.getDescription()).isEqualTo("Partly cloudy");
            assertThat(result.getTemperature()).isEqualTo(25.0);
        }

        @Test
        void returnsNoDataWhenRangeIsEmpty() {
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(null);

            WeatherData result = weatherService.getWeatherForCity("Nowhere", "2025-07-01");

            assertThat(result.getDescription()).isEqualTo("No data available");
            assertThat(result.getTemperature()).isEqualTo(0);
            assertThat(result.getHumidity()).isEqualTo(0);
        }

        @Test
        void throwsWhenDateIsBlank() {
            assertThatThrownBy(() -> weatherService.getWeatherForCity("Berlin", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startDate is required");
        }
    }

    // ── WMO code coverage ───────────────────────────────────────────────────

    @Nested
    class WmoCodeMapping {

        @Test
        void mapsAllKnownWeatherCodes() {
            // Verify representative codes from each category
            when(restTemplate.getForObject(contains("geocoding"), eq(GeocodingResponse.class)))
                    .thenReturn(geoResponse(52.52, 13.405));

            int[] codes = {0, 45, 51, 61, 71, 80, 95, 99};
            String[] expected = {"Clear sky", "Fog", "Light drizzle", "Slight rain",
                    "Slight snow", "Slight showers", "Thunderstorm", "Thunderstorm with heavy hail"};

            for (int i = 0; i < codes.length; i++) {
                when(restTemplate.getForObject(contains("forecast"), eq(ForecastResponse.class)))
                        .thenReturn(forecastResponse(
                                List.of("2025-07-01"),
                                List.of(20.0),
                                List.of(50),
                                List.of(codes[i])
                        ));

                List<WeatherData> result = weatherService.getWeatherForRange("Berlin", "2025-07-01", "2025-07-01");
                assertThat(result.get(0).getDescription())
                        .as("WMO code %d", codes[i])
                        .isEqualTo(expected[i]);
            }
        }

        @Test
        void rejectsRangesLongerThan16Days() {
            assertThatThrownBy(() -> weatherService.getWeatherForRange(
                    "Berlin", "2025-07-01", "2025-07-20"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("16 days or less");
        }

        @Test
        void rejectsEndDateBeforeStartDate() {
            assertThatThrownBy(() -> weatherService.getWeatherForRange(
                    "Berlin", "2025-07-03", "2025-07-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("on or after startDate");
        }
    }
}
