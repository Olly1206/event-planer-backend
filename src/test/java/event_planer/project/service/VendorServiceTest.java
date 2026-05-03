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

import event_planer.project.dto.VendorResponse;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock private RestTemplate restTemplate;
    private VendorService vendorService;

    @BeforeEach
    void setUp() {
        vendorService = new VendorService(restTemplate);
    }

    // ── Option → Tag Mapping ────────────────────────────────────────────────

    @Nested
    class MapOptionToTags {

        @SuppressWarnings("unchecked")
        private List<String> invokeMapping(String optionName) {
            try {
                Method m = VendorService.class.getDeclaredMethod("mapOptionToTags", String.class);
                m.setAccessible(true);
                return (List<String>) m.invoke(vendorService, optionName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void cateringMapsToCorrectTags() {
            List<String> filters = invokeMapping("Catering");
            assertThat(filters).anyMatch(f -> f.contains("caterer"));
            assertThat(filters).anyMatch(f -> f.contains("bakery"));
        }

        @Test
        void photographyMapsToCorrectTags() {
            List<String> filters = invokeMapping("Photography");
            assertThat(filters).anyMatch(f -> f.contains("photographer"));
            assertThat(filters).anyMatch(f -> f.contains("photo"));
        }

        @Test
        void musicMapsToCorrectTags() {
            List<String> filters = invokeMapping("Music");
            assertThat(filters).anyMatch(f -> f.contains("music_school"));
            assertThat(filters).anyMatch(f -> f.contains("musical_instruments"));
        }

        @Test
        void decorationMapsToCorrectTags() {
            List<String> filters = invokeMapping("Decoration");
            assertThat(filters).anyMatch(f -> f.contains("florist"));
            assertThat(filters).anyMatch(f -> f.contains("interior_decoration"));
        }

        @Test
        void securityMapsToCorrectTags() {
            List<String> filters = invokeMapping("Security");
            assertThat(filters).anyMatch(f -> f.contains("\"office\"=\"security\""));
        }

        @Test
        void securityStaffMapsToSecurityTags() {
            List<String> filters = invokeMapping("Security Staff");
            assertThat(filters).anyMatch(f -> f.contains("\"office\"=\"security\""));
        }

        @Test
        void avEquipmentMapsToCorrectTags() {
            List<String> filters = invokeMapping("AV Equipment");
            assertThat(filters).anyMatch(f -> f.contains("electronics"));
        }

        @Test
        void guestSpeakersMapsToSpeakerRelatedTags() {
            List<String> filters = invokeMapping("Guest Speakers");
            assertThat(filters).anyMatch(f -> f.contains("consulting"));
            assertThat(filters).anyMatch(f -> f.contains("speaker"));
        }

        @Test
        void printingMapsToCorrectTags() {
            List<String> filters = invokeMapping("Printing");
            assertThat(filters).anyMatch(f -> f.contains("copyshop"));
            assertThat(filters).anyMatch(f -> f.contains("printer"));
        }

        @Test
        void transportationMapsToCorrectTags() {
            List<String> filters = invokeMapping("Transportation");
            assertThat(filters).anyMatch(f -> f.contains("car_rental"));
            assertThat(filters).anyMatch(f -> f.contains("travel_agent"));
        }

        @Test
        void accommodationMapsToCorrectTags() {
            List<String> filters = invokeMapping("Accommodation");
            assertThat(filters).anyMatch(f -> f.contains("hotel"));
            assertThat(filters).anyMatch(f -> f.contains("hostel"));
        }

        @Test
        void cleaningMapsToCorrectTags() {
            List<String> filters = invokeMapping("Cleaning");
            assertThat(filters).anyMatch(f -> f.contains("laundry"));
            assertThat(filters).anyMatch(f -> f.contains("dry_cleaning"));
        }

        @Test
        void unknownOptionUsesNameFallback() {
            List<String> filters = invokeMapping("Balloon Artist");
            assertThat(filters).hasSize(1);
            assertThat(filters.get(0)).contains("name");
            assertThat(filters.get(0)).contains("Balloon Artist");
        }

        @Test
        void nullOptionReturnsEmptyList() {
            List<String> filters = invokeMapping(null);
            assertThat(filters).isEmpty();
        }

        @Test
        void caseInsensitiveMapping() {
            List<String> filters = invokeMapping("CATERING");
            assertThat(filters).anyMatch(f -> f.contains("caterer"));
        }
    }

    // ── Sanitise for Overpass ───────────────────────────────────────────────

    @Nested
    class SanitiseForOverpass {

        private String invokeSanitise(String input) {
            try {
                Method m = VendorService.class.getDeclaredMethod("sanitiseForOverpass", String.class);
                m.setAccessible(true);
                return (String) m.invoke(vendorService, input);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void stripsSpecialCharacters() {
            assertThat(invokeSanitise("Hello-World!@#$")).isEqualTo("HelloWorld");
        }

        @Test
        void preservesAlphanumericAndSpaces() {
            assertThat(invokeSanitise("DJ Service 2024")).isEqualTo("DJ Service 2024");
        }

        @Test
        void preservesGermanUmlauts() {
            assertThat(invokeSanitise("Blüten & Deko")).isEqualTo("Blüten  Deko");
        }
    }

    // ── getVendors ──────────────────────────────────────────────────────────

    @Nested
    class GetVendors {

        @Test
        void returnsEmptyWhenGeocodeFails() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(null));

            List<VendorResponse> result = vendorService.getVendors("Nowhere", 5000, "Catering");

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyForNullOption() {
            List<VendorResponse> result = vendorService.getVendors("Berlin", 5000, null);

            assertThat(result).isEmpty();
        }

        @Test
        void parsesValidOverpassResponse() {
            VendorService.NominatimResult nr = new VendorService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VendorService.NominatimResult[]{nr}));

            String json = """
                    {
                      "elements": [
                        {
                          "id": 999,
                          "lat": 52.51,
                          "lon": 13.39,
                          "tags": {
                            "name": "Mario's Catering",
                            "craft": "caterer",
                            "phone": "+49 30 9876543",
                            "website": "https://marios.de"
                          }
                        }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(json);

            List<VendorResponse> result = vendorService.getVendors("Berlin", 5000, "Catering");

            assertThat(result).hasSize(1);
            VendorResponse vendor = result.get(0);
            assertThat(vendor.getOsmId()).isEqualTo(999L);
            assertThat(vendor.getName()).isEqualTo("Mario's Catering");
            assertThat(vendor.getCategory()).isEqualTo("Caterer");
            assertThat(vendor.getOptionName()).isEqualTo("Catering");
            assertThat(vendor.getMatchedOptions()).containsExactly("Catering");
            assertThat(vendor.getPhone()).isEqualTo("+49 30 9876543");
            assertThat(vendor.getDistanceMeters()).isNotNull();
        }

        @Test
        void sortsResultsByDistanceAscending() {
            VendorService.NominatimResult nr = new VendorService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VendorService.NominatimResult[]{nr}));

            String json = """
                    {
                      "elements": [
                        { "id": 1, "lat": 52.560, "lon": 13.450, "tags": { "name": "Far Caterer", "craft": "caterer" } },
                        { "id": 2, "lat": 52.521, "lon": 13.406, "tags": { "name": "Near Caterer", "craft": "caterer" } }
                      ]
                    }
                    """;
            when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn(json);

            List<VendorResponse> result = vendorService.getVendors("Berlin", 5000, "Catering");

            assertThat(result).extracting(VendorResponse::getName)
                    .containsExactly("Near Caterer", "Far Caterer");
            assertThat(result.get(0).getDistanceMeters()).isLessThan(result.get(1).getDistanceMeters());
        }

        @Test
        void cachesRepeatedVendorSearches() {
            VendorService.NominatimResult nr = new VendorService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VendorService.NominatimResult[]{nr}));
            when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("""
                    {
                      "elements": [
                        { "id": 1, "lat": 52.521, "lon": 13.406, "tags": { "name": "Cached Caterer", "craft": "caterer" } }
                      ]
                    }
                    """);

            List<VendorResponse> first = vendorService.getVendors("Berlin", 5000, "Catering", "de");
            List<VendorResponse> second = vendorService.getVendors("Berlin", 5000, "Catering", "de");

            assertThat(first).hasSize(1);
            assertThat(second).hasSize(1);
            verify(restTemplate, times(1)).exchange(contains("countrycodes=de"), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class));
            verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
        }
    }

    // ── getVendorsForMultipleOptions ────────────────────────────────────────

    @Nested
    class GetVendorsForMultipleOptions {

        @Test
        void returnsEmptyWhenGeocodeFails() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(null));

            List<VendorResponse> result = vendorService.getVendorsForMultipleOptions(
                    "Nowhere", 5000, List.of("Catering", "Photography"));

            assertThat(result).isEmpty();
        }

        @Test
        void aggregatesResultsFromMultipleOptions() {
            VendorService.NominatimResult nr = new VendorService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VendorService.NominatimResult[]{nr}));

            String cateringJson = """
                    { "elements": [{ "id": 1, "lat": 52.5, "lon": 13.4, "tags": { "name": "Caterer A", "craft": "caterer" } }] }
                    """;
            String photoJson = """
                    { "elements": [{ "id": 2, "lat": 52.5, "lon": 13.4, "tags": { "name": "Photo B", "craft": "photographer" } }] }
                    """;

            // Each option triggers a separate Overpass call
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(cateringJson, photoJson);

            List<VendorResponse> result = vendorService.getVendorsForMultipleOptions(
                    "Berlin", 5000, List.of("Catering", "Photography"));

            assertThat(result).hasSize(2);
            assertThat(result).extracting(VendorResponse::getName)
                    .containsExactly("Caterer A", "Photo B");
        }

        @Test
        void deduplicatesSameVendorAcrossMultipleOptionsAndMergesMatches() {
            VendorService.NominatimResult nr = new VendorService.NominatimResult();
            nr.setLat("52.52");
            nr.setLon("13.405");
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                    eq(VendorService.NominatimResult[].class)))
                    .thenReturn(ResponseEntity.ok(new VendorService.NominatimResult[]{nr}));

            String cateringJson = """
                    { "elements": [{ "id": 7, "lat": 52.521, "lon": 13.406, "tags": { "name": "Studio X", "craft": "caterer" } }] }
                    """;
            String photographyJson = """
                    { "elements": [{ "id": 7, "lat": 52.521, "lon": 13.406, "tags": { "name": "Studio X", "craft": "photographer" } }] }
                    """;

            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn(cateringJson, photographyJson);

            List<VendorResponse> result = vendorService.getVendorsForMultipleOptions(
                    "Berlin", 5000, List.of("Catering", "Photography"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Studio X");
            assertThat(result.get(0).getMatchedOptions())
                    .containsExactly("Catering", "Photography");
        }
    }
}
