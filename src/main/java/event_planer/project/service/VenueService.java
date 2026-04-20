package event_planer.project.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import event_planer.project.dto.VenueResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches venue suggestions using a two-step OSM approach:
 *
 *   1. Nominatim geocodes the city name → lat/lon coordinates.
 *   2. Overpass API queries POIs by amenity/leisure tags within the given radius.
 *
 * Both APIs are free, require no API key and no credit card.
 * Germany has excellent OSM coverage, making this highly reliable.
 *
 * RestTemplate is injected from RestClientConfig with configured timeouts
 * to prevent indefinite hangs on slow external APIs.
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_RADIUS_METERS = 50_000;  // Cap at 50km to prevent timeout
    private static final int MIN_RADIUS_METERS = 500;     // Minimum 500m to avoid too many results

    public List<VenueResponse> getVenues(String city, int radiusMeters,
                                         String locationType, String eventType) {

        // Enforce radius bounds to prevent timeouts and excessive results
        int validRadius = Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));
        if (validRadius != radiusMeters) {
            // Log or silently clamp — silently for now
        }

        // Step 1: Geocode city name to coordinates
        double[] coords = geocodeCity(city);
        if (coords == null) return Collections.emptyList();

        // Step 2: Build Overpass query for matching venue types
        String overpassQuery = buildOverpassQuery(coords[0], coords[1],
                validRadius, locationType, eventType);

        // Step 3: Execute and parse
        return queryOverpass(overpassQuery);
    }

    // ── Step 1: Geocode via Nominatim ──────────────────────────────────────────

    private double[] geocodeCity(String city) {
        try {
            String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?q=" + encoded
                    + "&format=json&limit=1&addressdetails=0";

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EventPlannerApp/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            NominatimResult[] results = restTemplate
                    .exchange(url, HttpMethod.GET, entity, NominatimResult[].class)
                    .getBody();

            if (results == null || results.length == 0) return null;
            double lat = Double.parseDouble(results[0].lat);
            double lon = Double.parseDouble(results[0].lon);
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }

    // ── Step 2: Build Overpass QL query ────────────────────────────────────────

    private String buildOverpassQuery(double lat, double lon, int radius,
                                      String locationType, String eventType) {
        List<String> filters = buildTagFilters(locationType, eventType);

        // Build a union query that searches for nodes and ways matching any filter
        StringBuilder sb = new StringBuilder();
        sb.append("[out:json][timeout:25];\n(\n");

        for (String filter : filters) {
            sb.append("  nwr").append(filter)
              .append("(around:").append(radius).append(",")
              .append(lat).append(",").append(lon).append(");\n");
        }

        sb.append(");\nout center body;\n");
        return sb.toString();
    }

    /**
     * Maps locationType + eventType to Overpass tag filters with strict filtering.
     * - INDOOR: Only indoor venues (conference centers, theaters, nightclubs, etc.)
     * - OUTDOOR: Only outdoor venues (parks, gardens, pitches, etc.)
     * - BOTH/null: Include both indoor and outdoor options
     *
     * Uses well-established OSM tags that have good coverage in Germany.
     */
    private List<String> buildTagFilters(String locationType, String eventType) {
        List<String> filters = new ArrayList<>();

        // Strict mode: null means BOTH, otherwise exact match
        boolean isIndoorOnly = "INDOOR".equalsIgnoreCase(locationType);
        boolean isOutdoorOnly = "OUTDOOR".equalsIgnoreCase(locationType);
        boolean isBothOrUnspecified = locationType == null || "BOTH".equalsIgnoreCase(locationType);

        // ── INDOOR VENUES ──────────────────────────────────────────────────────

        if (isIndoorOnly || isBothOrUnspecified) {
            // Always include general indoor event/conference venues
            filters.add("[\"amenity\"=\"conference_centre\"]");
            filters.add("[\"amenity\"=\"events_venue\"]");
            filters.add("[\"amenity\"=\"community_centre\"]");

            if (eventType != null) {
                switch (eventType.toLowerCase()) {
                    case "seminar", "conference", "workshop" -> {
                        filters.add("[\"amenity\"=\"university\"]");
                        filters.add("[\"amenity\"=\"exhibition_centre\"]");
                        filters.add("[\"building\"=\"civic\"]");
                    }
                    case "party", "celebration", "networking" -> {
                        filters.add("[\"amenity\"=\"nightclub\"]");
                        filters.add("[\"leisure\"=\"dance\"]");
                    }
                    case "concert", "performance" -> {
                        filters.add("[\"amenity\"=\"theatre\"]");
                        filters.add("[\"amenity\"=\"music_venue\"]");
                        filters.add("[\"amenity\"=\"arts_centre\"]");
                    }
                    case "sports", "tournament" -> {
                        // Indoor sports venues only
                        filters.add("[\"leisure\"=\"sports_centre\"]");
                        filters.add("[\"building\"=\"sports_hall\"]");
                        filters.add("[\"leisure\"=\"ice_rink\"]");
                    }
                    case "exhibition", "fair" -> {
                        filters.add("[\"amenity\"=\"exhibition_centre\"]");
                    }
                    default -> filters.add("[\"amenity\"=\"events_venue\"]");
                }
            }
        }

        // ── OUTDOOR VENUES ────────────────────────────────────────────────────

        if (isOutdoorOnly || isBothOrUnspecified) {
            // General outdoor spaces (parks, gardens, squares)
            filters.add("[\"leisure\"=\"park\"][\"name\"]");
            filters.add("[\"leisure\"=\"garden\"][\"name\"]");
            filters.add("[\"amenity\"=\"public_square\"]");

            if (eventType != null) {
                switch (eventType.toLowerCase()) {
                    case "sports", "tournament" -> {
                        // Outdoor sports venues only
                        filters.add("[\"leisure\"=\"pitch\"]");
                        filters.add("[\"leisure\"=\"stadium\"][\"roof\"=\"no\"]");
                        filters.add("[\"leisure\"=\"basketball_court\"]");
                        filters.add("[\"leisure\"=\"tennis_court\"]");
                    }
                    case "concert", "performance" -> {
                        // Outdoor performance venues
                        filters.add("[\"leisure\"=\"bandstand\"]");
                        filters.add("[\"amenity\"=\"theatre\"][\"roof\"=\"no\"]");
                        filters.add("[\"amenity\"=\"stage\"]");
                    }
                    case "party", "celebration", "networking" -> {
                        // Outdoor party spaces
                        filters.add("[\"leisure\"=\"park\"][\"name\"]");
                        filters.add("[\"leisure\"=\"garden\"][\"name\"]");
                    }
                    default -> {
                        // Default outdoor: parks and gardens
                        filters.add("[\"leisure\"=\"park\"][\"name\"]");
                    }
                }
            }
        }

        return filters;
    }

    // ── Step 3: Query Overpass and parse results ───────────────────────────────

    private List<VenueResponse> queryOverpass(String overpassQuery) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "EventPlannerApp/1.0");

            String body = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            String response = restTemplate
                    .postForObject(OVERPASS_URL, entity, String.class);

            if (response == null) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) return Collections.emptyList();

            Map<Long, VenueResponse> seen = new LinkedHashMap<>();
            for (JsonNode el : elements) {
                if (seen.size() >= MAX_RESULTS) break;

                long id = el.get("id").asLong();
                if (seen.containsKey(id)) continue;

                JsonNode tags = el.get("tags");
                if (tags == null) continue;

                String name = getTag(tags, "name");
                if (name == null || name.isBlank()) continue;

                // Coordinates: nodes have lat/lon directly, ways/relations use "center"
                double lat, lon;
                if (el.has("lat")) {
                    lat = el.get("lat").asDouble();
                    lon = el.get("lon").asDouble();
                } else if (el.has("center")) {
                    lat = el.get("center").get("lat").asDouble();
                    lon = el.get("center").get("lon").asDouble();
                } else {
                    continue;
                }

                String address = buildAddressFromTags(tags);
                String category = determineCategory(tags);
                String website = getTag(tags, "website");
                String phone = getTag(tags, "phone");
                if (phone == null) phone = getTag(tags, "contact:phone");
                String openingHours = getTag(tags, "opening_hours");

                seen.put(id, new VenueResponse(id, name, address, lat, lon,
                        category, website, phone, openingHours));
            }

            return new ArrayList<>(seen.values());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String getTag(JsonNode tags, String key) {
        JsonNode node = tags.get(key);
        return node != null ? node.asText() : null;
    }

    private String buildAddressFromTags(JsonNode tags) {
        String street = getTag(tags, "addr:street");
        String houseNumber = getTag(tags, "addr:housenumber");
        String city = getTag(tags, "addr:city");
        String postcode = getTag(tags, "addr:postcode");

        StringBuilder sb = new StringBuilder();
        if (street != null) {
            sb.append(street);
            if (houseNumber != null) sb.append(" ").append(houseNumber);
        }
        if (city != null) {
            if (sb.length() > 0) sb.append(", ");
            if (postcode != null) sb.append(postcode).append(" ");
            sb.append(city);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String determineCategory(JsonNode tags) {
        // Check amenity first, then leisure, then building
        String amenity = getTag(tags, "amenity");
        if (amenity != null) return formatTag(amenity);
        String leisure = getTag(tags, "leisure");
        if (leisure != null) return formatTag(leisure);
        String building = getTag(tags, "building");
        if (building != null) return formatTag(building);
        return "venue";
    }

    /** Converts "conference_centre" → "Conference Centre" */
    private String formatTag(String tag) {
        String[] parts = tag.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ── Nominatim DTO (only used for geocoding now) ────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NominatimResult {
        String lat;
        String lon;
    }
}
