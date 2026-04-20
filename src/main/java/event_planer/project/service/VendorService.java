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

import event_planer.project.dto.VendorResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches vendor / service-provider suggestions from OpenStreetMap via Overpass.
 *
 * Flow:  city name  →  Nominatim geocode  →  Overpass query by craft/shop/office tags
 *        →  list of nearby vendors matching the requested EventOption name.
 *
 * Supported option-to-tag mappings (based on well-tagged German OSM data):
 *   Catering       → craft=caterer, amenity=restaurant (catering tag), shop=deli
 *   Photography    → craft=photographer, shop=photo
 *   Music / DJ     → amenity=music_school, shop=musical_instruments
 *   Decoration     → shop=florist, shop=interior_decoration
 *   Security       → office=security, shop=security
 *   AV Equipment   → shop=electronics, office=it
 *   Printing       → shop=copyshop, craft=printer
 *
 * Both APIs are free, require no API key, no credit card.
 * RestTemplate is injected from RestClientConfig with configured timeouts
 * to prevent indefinite hangs on slow external APIs.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OVERPASS_URL  = "https://overpass-api.de/api/interpreter";
    private static final int    MAX_RESULTS   = 20;
    private static final int    MAX_RADIUS_METERS = 50_000;  // Cap at 50km to prevent timeout
    private static final int    MIN_RADIUS_METERS = 500;     // Minimum 500m to avoid too many results

    /**
     * Returns vendor suggestions near the given city for a specific event-option name.
     *
     * @param city         city name to geocode (e.g. "Berlin")
     * @param radiusMeters search radius (default 5 000 m, capped at 50 km)
     * @param optionName   the EventOption name, e.g. "Catering", "Photography"
     */
    public List<VendorResponse> getVendors(String city, int radiusMeters, String optionName) {
        // Enforce radius bounds to prevent timeouts
        int validRadius = Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));
        
        double[] coords = geocodeCity(city);
        if (coords == null) return Collections.emptyList();

        List<String> filters = mapOptionToTags(optionName);
        if (filters.isEmpty()) return Collections.emptyList();

        String query = buildOverpassQuery(coords[0], coords[1], validRadius, filters);
        return queryOverpass(query, optionName);
    }

    /**
     * Returns vendor suggestions for ALL options selected on an event at once.
     * Useful for a one-shot "Show me all service providers I might need" call.
     *
     * @param city          city name
     * @param radiusMeters  search radius (capped at 50 km)
     * @param optionNames   set of EventOption names, e.g. ["Catering", "Photography"]
     */
    public List<VendorResponse> getVendorsForMultipleOptions(
            String city, int radiusMeters, List<String> optionNames) {

        // Enforce radius bounds to prevent timeouts
        int validRadius = Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));

        double[] coords = geocodeCity(city);
        if (coords == null) return Collections.emptyList();

        List<VendorResponse> all = new ArrayList<>();
        for (String optionName : optionNames) {
            List<String> filters = mapOptionToTags(optionName);
            if (filters.isEmpty()) continue;

            String query = buildOverpassQuery(coords[0], coords[1], validRadius, filters);
            List<VendorResponse> results = queryOverpass(query, optionName);
            all.addAll(results);
        }
        return all;
    }

    // ── Geocode via Nominatim ──────────────────────────────────────────────────

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
            return new double[]{
                Double.parseDouble(results[0].lat),
                Double.parseDouble(results[0].lon)
            };
        } catch (Exception e) {
            return null;
        }
    }

    // ── Option → OSM tag mapping ───────────────────────────────────────────────

    /**
     * Maps a human-readable EventOption name to a list of Overpass tag filters.
     * Each filter is an Overpass QL selector like ["craft"="caterer"].
     * The mapping is intentionally broad so that German OSM data returns results.
     */
    private List<String> mapOptionToTags(String optionName) {
        if (optionName == null) return Collections.emptyList();

        List<String> filters = new ArrayList<>();
        switch (optionName.toLowerCase().trim()) {
            case "catering" -> {
                filters.add("[\"craft\"=\"caterer\"]");
                filters.add("[\"amenity\"=\"restaurant\"][\"catering\"=\"yes\"]");
                filters.add("[\"shop\"=\"deli\"]");
                filters.add("[\"shop\"=\"bakery\"]");
            }
            case "photography" -> {
                filters.add("[\"craft\"=\"photographer\"]");
                filters.add("[\"shop\"=\"photo\"]");
            }
            case "music", "dj", "live music" -> {
                filters.add("[\"amenity\"=\"music_school\"]");
                filters.add("[\"shop\"=\"musical_instruments\"]");
                filters.add("[\"craft\"=\"musical_instrument\"]");
            }
            case "decoration", "flowers", "florist" -> {
                filters.add("[\"shop\"=\"florist\"]");
                filters.add("[\"shop\"=\"interior_decoration\"]");
                filters.add("[\"shop\"=\"garden_centre\"]");
            }
            case "security" -> {
                filters.add("[\"office\"=\"security\"]");
                filters.add("[\"shop\"=\"security\"]");
            }
            case "av equipment", "audio/visual", "technology" -> {
                filters.add("[\"shop\"=\"electronics\"]");
                filters.add("[\"office\"=\"it\"]");
                filters.add("[\"shop\"=\"hifi\"]");
            }
            case "printing", "print" -> {
                filters.add("[\"shop\"=\"copyshop\"]");
                filters.add("[\"craft\"=\"printer\"]");
                filters.add("[\"shop\"=\"stationery\"]");
            }
            case "transportation", "transport", "shuttle" -> {
                filters.add("[\"amenity\"=\"car_rental\"]");
                filters.add("[\"amenity\"=\"bus_station\"]");
                filters.add("[\"office\"=\"travel_agent\"]");
            }
            case "accommodation", "hotel", "lodging" -> {
                filters.add("[\"tourism\"=\"hotel\"]");
                filters.add("[\"tourism\"=\"hostel\"]");
                filters.add("[\"tourism\"=\"guest_house\"]");
            }
            case "cleaning" -> {
                filters.add("[\"shop\"=\"laundry\"]");
                filters.add("[\"shop\"=\"dry_cleaning\"]");
            }
            default -> {
                // Fallback: search by name tag containing the option name
                filters.add("[\"name\"~\"" + sanitiseForOverpass(optionName) + "\",i]");
            }
        }
        return filters;
    }

    /** Escapes characters that could break an Overpass regex literal. */
    private String sanitiseForOverpass(String input) {
        return input.replaceAll("[^a-zA-Z0-9äöüÄÖÜß ]", "");
    }

    // ── Build Overpass QL query ────────────────────────────────────────────────

    private String buildOverpassQuery(double lat, double lon, int radius,
                                      List<String> filters) {
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

    // ── Query Overpass and parse results ────────────────────────────────────────

    private List<VendorResponse> queryOverpass(String overpassQuery, String optionName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "EventPlannerApp/1.0");

            String body = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(OVERPASS_URL, entity, String.class);
            if (response == null) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) return Collections.emptyList();

            Map<Long, VendorResponse> seen = new LinkedHashMap<>();
            for (JsonNode el : elements) {
                if (seen.size() >= MAX_RESULTS) break;

                long id = el.get("id").asLong();
                if (seen.containsKey(id)) continue;

                JsonNode tags = el.get("tags");
                if (tags == null) continue;

                String name = getTag(tags, "name");
                if (name == null || name.isBlank()) continue;

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

                String address  = buildAddressFromTags(tags);
                String category = determineCategory(tags);
                String website  = getTag(tags, "website");
                String phone    = getTag(tags, "phone");
                if (phone == null) phone = getTag(tags, "contact:phone");
                String hours    = getTag(tags, "opening_hours");

                seen.put(id, new VendorResponse(
                        id, name, address, lat, lon,
                        category, optionName, website, phone, hours));
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
        String street      = getTag(tags, "addr:street");
        String houseNumber = getTag(tags, "addr:housenumber");
        String city        = getTag(tags, "addr:city");
        String postcode    = getTag(tags, "addr:postcode");

        StringBuilder sb = new StringBuilder();
        if (street != null) {
            sb.append(street);
            if (houseNumber != null) sb.append(" ").append(houseNumber);
        }
        if (city != null) {
            if (!sb.isEmpty()) sb.append(", ");
            if (postcode != null) sb.append(postcode).append(" ");
            sb.append(city);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String determineCategory(JsonNode tags) {
        String craft   = getTag(tags, "craft");
        if (craft != null) return capitalise(craft);

        String shop    = getTag(tags, "shop");
        if (shop != null) return capitalise(shop);

        String office  = getTag(tags, "office");
        if (office != null) return capitalise(office);

        String amenity = getTag(tags, "amenity");
        if (amenity != null) return capitalise(amenity);

        String tourism = getTag(tags, "tourism");
        if (tourism != null) return capitalise(tourism);

        return "Vendor";
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace('_', ' ');
    }

    // ── Nominatim DTO ──────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NominatimResult {
        String lat;
        String lon;
    }
}
