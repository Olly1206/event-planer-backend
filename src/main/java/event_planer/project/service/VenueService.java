package event_planer.project.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches venue suggestions using a two-step OSM approach:
 *
 *   1. Nominatim geocodes the city name → lat/lon coordinates.
 *   2. Overpass API queries POIs by amenity/leisure tags within the given radius.
 *
 * Results are ranked by distance so the client gets nearby, usable venues first.
 * Repeated geocode/search requests are cached in-memory for a short time to reduce
 * pressure on Nominatim and Overpass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VenueService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final int MIN_RADIUS_METERS = 500;
    private static final long GEOCODE_CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final long SEARCH_CACHE_TTL_MS = 10L * 60L * 1000L;

    private final Map<String, CacheEntry<double[]>> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<VenueResponse>>> searchCache = new ConcurrentHashMap<>();

    public List<VenueResponse> getVenues(String city, int radiusMeters,
                                         String locationType, String eventType) {
        return getVenues(city, radiusMeters, locationType, eventType, null);
    }

    public List<VenueResponse> getVenues(String city, int radiusMeters,
                                         String locationType, String eventType,
                                         String countryCode) {

        String normalisedCountryCode = normaliseCountryCode(countryCode);
        log.info("Fetching venues for city={}, countryCode={}, radiusMeters={}, locationType={}, eventType={}",
                city, normalisedCountryCode, radiusMeters, locationType, eventType);

        int validRadius = Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));
        if (validRadius != radiusMeters) {
            log.debug("Clamped radius from {} to {}", radiusMeters, validRadius);
        }

        String cacheKey = buildSearchCacheKey(city, validRadius, locationType, eventType, normalisedCountryCode);
        List<VenueResponse> cachedResults = getCachedList(searchCache, cacheKey);
        if (cachedResults != null) {
            log.debug("Returning {} cached venues for {}", cachedResults.size(), cacheKey);
            return cachedResults;
        }

        double[] coords = geocodeCity(city, normalisedCountryCode);
        if (coords == null) {
            log.warn("Failed to geocode city: {}", city);
            return Collections.emptyList();
        }

        String overpassQuery = buildOverpassQuery(coords[0], coords[1], validRadius, locationType, eventType);
        List<VenueResponse> results = queryOverpass(overpassQuery, coords[0], coords[1]);

        putCachedList(searchCache, cacheKey, results, SEARCH_CACHE_TTL_MS);
        log.info("Found {} venues for city {}", results.size(), city);
        return copyVenueList(results);
    }

    // ── Step 1: Geocode via Nominatim ──────────────────────────────────────────

    private double[] geocodeCity(String city, String countryCode) {
        String cacheKey = (city == null ? "" : city.trim().toLowerCase(Locale.ROOT)) + "|" + countryCode;
        CacheEntry<double[]> cached = geocodeCache.get(cacheKey);
        if (isValid(cached)) {
            return cached.value.clone();
        }

        try {
            String encoded = URLEncoder.encode(city, StandardCharsets.UTF_8);
            StringBuilder url = new StringBuilder(NOMINATIM_URL)
                    .append("?q=").append(encoded)
                    .append("&format=json&limit=1&addressdetails=0");
            if (countryCode != null) {
                url.append("&countrycodes=").append(countryCode);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "EventPlannerApp/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            NominatimResult[] results = restTemplate
                    .exchange(url.toString(), HttpMethod.GET, entity, NominatimResult[].class)
                    .getBody();

            if (results == null || results.length == 0) {
                return null;
            }

            double[] coords = new double[]{
                    Double.parseDouble(results[0].lat),
                    Double.parseDouble(results[0].lon)
            };
            geocodeCache.put(cacheKey, new CacheEntry<>(coords.clone(), System.currentTimeMillis() + GEOCODE_CACHE_TTL_MS));
            return coords;
        } catch (Exception e) {
            log.error("Error geocoding city {}: {}", city, e.getMessage(), e);
            return null;
        }
    }

    // ── Step 2: Build Overpass QL query ────────────────────────────────────────

    private String buildOverpassQuery(double lat, double lon, int radius,
                                      String locationType, String eventType) {
        List<String> filters = buildTagFilters(locationType, eventType);

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
     * Maps locationType + eventType to Overpass tag filters with stricter filtering.
     * Broad fallbacks like random grassy land are intentionally excluded because they
     * produce poor venue suggestions for end users.
     */
    private List<String> buildTagFilters(String locationType, String eventType) {
        List<String> filters = new ArrayList<>();

        boolean isIndoorOnly = "INDOOR".equalsIgnoreCase(locationType);
        boolean isOutdoorOnly = "OUTDOOR".equalsIgnoreCase(locationType);
        boolean isBothOrUnspecified = locationType == null || "BOTH".equalsIgnoreCase(locationType);

        if (isIndoorOnly || isBothOrUnspecified) {
            filters.add("[\"amenity\"=\"conference_centre\"]");
            filters.add("[\"amenity\"=\"events_venue\"]");
            filters.add("[\"amenity\"=\"community_centre\"]");

            if (eventType != null) {
                switch (eventType.toLowerCase(Locale.ROOT)) {
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
                        filters.add("[\"leisure\"=\"sports_centre\"]");
                        filters.add("[\"building\"=\"sports_hall\"]");
                        filters.add("[\"leisure\"=\"ice_rink\"]");
                    }
                    case "exhibition", "fair" -> filters.add("[\"amenity\"=\"exhibition_centre\"]");
                    default -> filters.add("[\"amenity\"=\"events_venue\"]");
                }
            }
        }

        if (isOutdoorOnly || isBothOrUnspecified) {
            filters.add("[\"leisure\"=\"park\"]");
            filters.add("[\"leisure\"=\"garden\"]");
            filters.add("[\"amenity\"=\"public_square\"]");

            if (eventType != null) {
                switch (eventType.toLowerCase(Locale.ROOT)) {
                    case "sports", "tournament" -> {
                        filters.add("[\"leisure\"=\"pitch\"]");
                        filters.add("[\"leisure\"=\"stadium\"]");
                        filters.add("[\"leisure\"=\"basketball_court\"]");
                        filters.add("[\"leisure\"=\"tennis_court\"]");
                    }
                    case "concert", "performance" -> {
                        filters.add("[\"leisure\"=\"bandstand\"]");
                        filters.add("[\"amenity\"=\"stage\"]");
                    }
                    case "party", "celebration", "networking", "wedding" -> {
                        filters.add("[\"leisure\"=\"park\"]");
                        filters.add("[\"leisure\"=\"garden\"]");
                        filters.add("[\"amenity\"=\"public_square\"]");
                    }
                    default -> {
                        filters.add("[\"leisure\"=\"park\"]");
                        filters.add("[\"leisure\"=\"garden\"]");
                    }
                }
            }
        }

        return filters;
    }

    // ── Step 3: Query Overpass and parse results ───────────────────────────────

    private List<VenueResponse> queryOverpass(String overpassQuery, double originLat, double originLon) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "EventPlannerApp/1.0");

            String body = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(OVERPASS_URL, entity, String.class);
            if (response == null) {
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode elements = root.get("elements");
            if (elements == null || !elements.isArray()) {
                return Collections.emptyList();
            }

            Map<Long, VenueResponse> seen = new LinkedHashMap<>();
            for (JsonNode el : elements) {
                long id = el.get("id").asLong();
                if (seen.containsKey(id)) {
                    continue;
                }

                JsonNode tags = el.get("tags");
                if (tags == null) {
                    continue;
                }

                String name = getTag(tags, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }

                double lat;
                double lon;
                if (el.has("lat")) {
                    lat = el.get("lat").asDouble();
                    lon = el.get("lon").asDouble();
                } else if (el.has("center")) {
                    lat = el.get("center").get("lat").asDouble();
                    lon = el.get("center").get("lon").asDouble();
                } else {
                    continue;
                }

                int distanceMeters = calculateDistanceMeters(originLat, originLon, lat, lon);
                String address = buildAddressFromTags(tags);
                String category = determineCategory(tags);
                String website = getTag(tags, "website");
                String phone = getTag(tags, "phone");
                if (phone == null) {
                    phone = getTag(tags, "contact:phone");
                }
                String openingHours = getTag(tags, "opening_hours");

                seen.put(id, new VenueResponse(id, name, address, lat, lon, distanceMeters,
                        category, website, phone, openingHours));
            }

            return seen.values().stream()
                    .sorted(Comparator
                            .comparing(VenueResponse::getDistanceMeters, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(VenueResponse::getName, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_RESULTS)
                    .map(this::copyVenue)
                    .toList();
        } catch (Exception e) {
            log.error("Error querying Overpass API: {}", e.getMessage(), e);
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
            if (houseNumber != null) {
                sb.append(" ").append(houseNumber);
            }
        }
        if (city != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            if (postcode != null) {
                sb.append(postcode).append(" ");
            }
            sb.append(city);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String determineCategory(JsonNode tags) {
        String amenity = getTag(tags, "amenity");
        if (amenity != null) {
            return formatTag(amenity);
        }
        String leisure = getTag(tags, "leisure");
        if (leisure != null) {
            return formatTag(leisure);
        }
        String building = getTag(tags, "building");
        if (building != null) {
            return formatTag(building);
        }
        return "Venue";
    }

    private String formatTag(String tag) {
        String[] parts = tag.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private int calculateDistanceMeters(double originLat, double originLon, double targetLat, double targetLon) {
        double earthRadiusMeters = 6_371_000d;
        double latDistance = Math.toRadians(targetLat - originLat);
        double lonDistance = Math.toRadians(targetLon - originLon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(originLat)) * Math.cos(Math.toRadians(targetLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(earthRadiusMeters * c);
    }

    private String normaliseCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        String normalised = countryCode.trim().toLowerCase(Locale.ROOT);
        if (!normalised.matches("[a-z]{2}")) {
            throw new IllegalArgumentException("countryCode must be a 2-letter ISO country code");
        }
        return normalised;
    }

    private String buildSearchCacheKey(String city, int radiusMeters,
                                       String locationType, String eventType,
                                       String countryCode) {
        return String.join("|",
                city == null ? "" : city.trim().toLowerCase(Locale.ROOT),
                String.valueOf(radiusMeters),
                locationType == null ? "" : locationType.trim().toLowerCase(Locale.ROOT),
                eventType == null ? "" : eventType.trim().toLowerCase(Locale.ROOT),
                countryCode == null ? "" : countryCode);
    }

    private List<VenueResponse> getCachedList(Map<String, CacheEntry<List<VenueResponse>>> cache, String key) {
        CacheEntry<List<VenueResponse>> entry = cache.get(key);
        if (!isValid(entry)) {
            cache.remove(key);
            return null;
        }
        return copyVenueList(entry.value);
    }

    private void putCachedList(Map<String, CacheEntry<List<VenueResponse>>> cache, String key,
                               List<VenueResponse> results, long ttlMillis) {
        cache.put(key, new CacheEntry<>(copyVenueList(results), System.currentTimeMillis() + ttlMillis));
    }

    private boolean isValid(CacheEntry<?> entry) {
        return entry != null && entry.expiresAtMillis > System.currentTimeMillis();
    }

    private List<VenueResponse> copyVenueList(List<VenueResponse> results) {
        return results.stream().map(this::copyVenue).toList();
    }

    private VenueResponse copyVenue(VenueResponse venue) {
        return new VenueResponse(
                venue.getOsmId(),
                venue.getName(),
                venue.getAddress(),
                venue.getLat(),
                venue.getLon(),
                venue.getDistanceMeters(),
                venue.getCategory(),
                venue.getWebsite(),
                venue.getPhone(),
                venue.getOpeningHours()
        );
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NominatimResult {
        String lat;
        String lon;
    }

    private record CacheEntry<T>(T value, long expiresAtMillis) {
    }
}
