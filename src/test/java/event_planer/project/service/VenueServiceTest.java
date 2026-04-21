package event_planer.project.service;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import event_planer.project.dto.VenueResponse;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock private RestTemplate restTemplate;
    private VenueService venueService;

    @BeforeEach
    void setUp() {
        venueService = new VenueService(restTemplate);
    }

    // ── Tag filter logic ────────────────────────────────────────────────────

    @Nested
    class BuildTagFilters {

        @SuppressWarnings("unchecked")
        private List<String> invokeFilters(String locationType, String eventType) {
            try {
                Method m = VenueService.class.getDeclaredMethod(
                        "buildTagFilters", String.class, String.class);
                m.setAccessible(true);
                return (List<String>) m.invoke(venueService, locationType, eventType);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void indoorSeminarIncludesUniversityAndExhibition() {
            List<String> filters = invokeFilters("INDOOR", "seminar");
            assertThat(filters).anyMatch(f -> f.contains("university"));
            assertThat(filters).anyMatch(f -> f.contains("exhibition_centre"));
        }

        @Test
        void indoorPartyIncludesNightclubAndDance() {
            List<String> filters = invokeFilters("INDOOR", "party");
            assertThat(filters).anyMatch(f -> f.contains("nightclub"));
            assertThat(filters).anyMatch(f -> f.contains("dance"));
        }

        @Test
        void indoorConcertIncludesTheatreAndMusicVenue() {
            List<String> filters = invokeFilters("INDOOR", "concert");
            assertThat(filters).anyMatch(f -> f.contains("theatre"));
            assertThat(filters).anyMatch(f -> f.contains("music_venue"));
        }

        @Test
        void indoorSportsIncludesSportsCentre() {
            List<String> filters = invokeFilters("INDOOR", "sports");
            assertThat(filters).anyMatch(f -> f.contains("sports_centre"));
        }

        @Test
        void outdoorIncludesParkAndGarden() {
            List<String> filters = invokeFilters("OUTDOOR", null);
            assertThat(filters).anyMatch(f -> f.contains("park"));
            assertThat(filters).anyMatch(f -> f.contains("garden"));
        }

        @Test
        void outdoorExcludesIndoorVenues() {
            List<String> filters = invokeFilters("OUTDOOR", null);
            assertThat(filters).noneMatch(f -> f.contains("conference_centre"));
        }

        @Test
        void outdoorExcludesLowSignalGrassFallback() {
            List<String> filters = invokeFilters("OUTDOOR", null);
            assertThat(filters).noneMatch(f -> f.contains("landuse"));
        }

        @Test
        void bothIncludesIndoorAndOutdoor() {
            List<String> filters = invokeFilters("BOTH", "sports");
            assertThat(filters).anyMatch(f -> f.contains("sports_centre"));
            assertThat(filters).anyMatch(f -> f.contains("park"));
        }

        @Test
        void nullLocationTypeDefaultsToBoth() {
            List<String> filters = invokeFilters(null, null);
            assertThat(filters).anyMatch(f -> f.contains("conference_centre"));
            assertThat(filters).anyMatch(f -> f.contains("park"));
        }

        @Test
        void outdoorSportsIncludesPitchAndStadium() {
            List<String> filters = invokeFilters("OUTDOOR", "sports");
            assertThat(filters).anyMatch(f -> f.contains("pitch"));
            assertThat(filters).anyMatch(f -> f.contains("stadium"));
        }

        @Test
        void unknownEventTypeFallsBackToEventsVenue() {
            List<String> filters = invokeFilters("INDOOR", "unknown_type");
            assertThat(filters).anyMatch(f -> f.contains("events_venue"));
        }
    }

    // ── getVenues ───────────────────────────────────────────────────────────

    @Nested
    class GetVenues {

        @Test
        void returnsEmptyWhenGeocodeFails() {
            // Nominatim returns null body
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(null));

            List<VenueResponse> result = venueService.getVenues("Nowhere", 5000, "INDOOR", "party");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenGeocodeReturnsEmptyArray() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[0]));

            List<VenueResponse> result = venueService.getVenues("Nowhere", 5000, "INDOOR", "party");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenOverpassCallFails() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(null);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "INDOOR", "party");

            assertThat(result).isEmpty();
        }

        @Test
        void parsesValidOverpassResponse() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));

            String overpassJson = """
                    {
                      "elements": [
                        {
                          "id": 12345,
                          "lat": 52.521,
                          "lon": 13.410,
                          "tags": {
                            "name": "Test Venue",
                            "amenity": "conference_centre",
                            "addr:street": "Unter den Linden",
                            "addr:housenumber": "1",
                            "addr:city": "Berlin",
                            "addr:postcode": "10117",
                            "website": "https://example.com",
                            "phone": "+49 30 1234567"
                          }
                        }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(overpassJson);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "INDOOR", "conference");

            assertThat(result).hasSize(1);
            VenueResponse venue = result.get(0);
            assertThat(venue.getOsmId()).isEqualTo(12345L);
            assertThat(venue.getName()).isEqualTo("Test Venue");
            assertThat(venue.getAddress()).isEqualTo("Unter den Linden 1, 10117 Berlin");
            assertThat(venue.getCategory()).isEqualTo("Conference Centre");
            assertThat(venue.getWebsite()).isEqualTo("https://example.com");
            assertThat(venue.getPhone()).isEqualTo("+49 30 1234567");
            assertThat(venue.getDistanceMeters()).isNotNull();
        }

        @Test
        void skipsElementsWithoutName() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));

            String overpassJson = """
                    {
                      "elements": [
                        { "id": 1, "lat": 52.5, "lon": 13.4, "tags": { "amenity": "park" } },
                        { "id": 2, "lat": 52.5, "lon": 13.4, "tags": { "name": "Good Park", "leisure": "park" } }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(overpassJson);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "OUTDOOR", null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Good Park");
        }

        @Test
        void deduplicatesById() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));

            String overpassJson = """
                    {
                      "elements": [
                        { "id": 100, "lat": 52.5, "lon": 13.4, "tags": { "name": "Park A", "leisure": "park" } },
                        { "id": 100, "lat": 52.5, "lon": 13.4, "tags": { "name": "Park A", "leisure": "park" } }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(overpassJson);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "OUTDOOR", null);

            assertThat(result).hasSize(1);
        }

        @Test
        void useCenterCoordsForWays() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));

            String overpassJson = """
                    {
                      "elements": [
                        {
                          "id": 200,
                          "type": "way",
                          "center": { "lat": 52.530, "lon": 13.420 },
                          "tags": { "name": "Big Hall", "building": "civic" }
                        }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(overpassJson);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "INDOOR", "seminar");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLat()).isEqualTo(52.530);
            assertThat(result.get(0).getLon()).isEqualTo(13.420);
        }

        @Test
        void sortsResultsByDistanceAscending() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));

            String overpassJson = """
                    {
                      "elements": [
                        { "id": 1, "lat": 52.560, "lon": 13.450, "tags": { "name": "Far Venue", "amenity": "conference_centre" } },
                        { "id": 2, "lat": 52.521, "lon": 13.406, "tags": { "name": "Near Venue", "amenity": "conference_centre" } }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(overpassJson);

            List<VenueResponse> result = venueService.getVenues("Berlin", 5000, "INDOOR", "conference");

            assertThat(result).extracting(VenueResponse::getName)
                    .containsExactly("Near Venue", "Far Venue");
            assertThat(result.get(0).getDistanceMeters()).isLessThan(result.get(1).getDistanceMeters());
        }

        @Test
        void cachesRepeatedVenueSearches() {
            VenueService.NominatimResult nr = new VenueService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VenueService.NominatimResult[]{nr}));
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn("""
                            {
                              "elements": [
                                { "id": 1, "lat": 52.521, "lon": 13.406, "tags": { "name": "Cached Venue", "amenity": "conference_centre" } }
                              ]
                            }
                            """);

            List<VenueResponse> first = venueService.getVenues("Berlin", 5000, "INDOOR", "conference", "de");
            List<VenueResponse> second = venueService.getVenues("Berlin", 5000, "INDOOR", "conference", "de");

            assertThat(first).hasSize(1);
            assertThat(second).hasSize(1);
            verify(restTemplate, times(1)).exchange(contains("countrycodes=de"), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VenueService.NominatimResult[].class));
            verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
        }
    }
}
