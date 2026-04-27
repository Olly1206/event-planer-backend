package event_planer.project.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import event_planer.project.dto.VendorResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fetches vendor / service-provider suggestions from OpenStreetMap via Overpass.
 *
 * Results are ranked by distance and deduplicated across multi-option searches so
 * the client gets a clean "who is actually nearby" list instead of raw Overpass order.
 * Repeated geocode/search requests are cached in-memory to reduce external API load.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    private static final String OVERPASS_URL  = "https://overpass-api.de/api/interpreter";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_RADIUS_METERS = 50_000;
    private static final int MIN_RADIUS_METERS = 500;
    private static final long GEOCODE_CACHE_TTL_MS = 30L * 60L * 1000L;
    private static final long SEARCH_CACHE_TTL_MS = 10L * 60L * 1000L;

    private final Map<String, CacheEntry<double[]>> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<VendorResponse>>> searchCache = new ConcurrentHashMap<>();

    public List<VendorResponse> getVendors(String city, int radiusMeters, String optionName) {
        return getVendors(city, radiusMeters, optionName, null);
    }

    public List<VendorResponse> getVendors(String city, int radiusMeters, String optionName,
                                           String countryCode) {
        if (optionName == null) {
            return Collections.emptyList();
        }

        return getVendorsForMultipleOptions(city, radiusMeters, List.of(optionName), countryCode);
    }

    public List<VendorResponse> getVendorsForMultipleOptions(
            String city, int radiusMeters, List<String> optionNames) {
        return getVendorsForMultipleOptions(city, radiusMeters, optionNames, null);
    }

    public List<VendorResponse> getVendorsForMultipleOptions(
            String city, int radiusMeters, List<String> optionNames, String countryCode) {

        if (optionNames == null || optionNames.isEmpty()) {
            return Collections.emptyList();
        }

        String normalisedCountryCode = normaliseCountryCode(countryCode);
        int validRadius = Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));
        String cacheKey = buildSearchCacheKey(city, validRadius, optionNames, normalisedCountryCode);

        List<VendorResponse> cachedResults = getCachedList(cacheKey);
        if (cachedResults != null) {
            return cachedResults;
        }

        double[] coords = geocodeCity(city, normalisedCountryCode);
        if (coords == null) {
            return Collections.emptyList();
        }

        Map<Long, VendorAccumulator> merged = new LinkedHashMap<>();
        for (String optionName : optionNames) {
            List<String> filters = mapOptionToTags(optionName);
            if (filters.isEmpty()) {
                continue;
            }

            String query = buildOverpassQuery(coords[0], coords[1], validRadius, filters);
            List<VendorResponse> results = queryOverpass(query, coords[0], coords[1], optionName);
            mergeVendors(merged, results);
        }

        List<VendorResponse> deduped = merged.values().stream()
                .map(VendorAccumulator::toResponse)
                .sorted(Comparator
                        .comparing(VendorResponse::getDistanceMeters, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(VendorResponse::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_RESULTS)
                .map(this::copyVendor)
                .toList();

        putCachedList(cacheKey, deduped);
        return copyVendorList(deduped);
    }

    // ── Geocode via Nominatim ──────────────────────────────────────────────────

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
            return null;
        }
    }

    // ── Option → OSM tag mapping ───────────────────────────────────────────────

    private List<String> mapOptionToTags(String optionName) {
        if (optionName == null) {
            return Collections.emptyList();
        }

        List<String> filters = new ArrayList<>();
        switch (optionName.toLowerCase(Locale.ROOT).trim()) {
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
            default -> filters.add("[\"name\"~\"" + sanitiseForOverpass(optionName) + "\",i]");
        }
        return filters;
    }

    private String sanitiseForOverpass(String input) {
        return input.replaceAll("[^a-zA-Z0-9äöüÄÖÜß ]", "");
    }

    // ── Build Overpass QL query ────────────────────────────────────────────────

    private String buildOverpassQuery(double lat, double lon, int radius, List<String> filters) {
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

    private List<VendorResponse> queryOverpass(String overpassQuery, double originLat, double originLon,
                                               String optionName) {
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

            Map<Long, VendorResponse> seen = new LinkedHashMap<>();
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
                String email = getTag(tags, "email");
                if (email == null) {
                    email = getTag(tags, "contact:email");
                }
                String phone = getTag(tags, "phone");
                if (phone == null) {
                    phone = getTag(tags, "contact:phone");
                }
                String hours = getTag(tags, "opening_hours");

                seen.put(id, new VendorResponse(
                        id, name, address, lat, lon, distanceMeters,
                        category, optionName, new ArrayList<>(List.of(optionName)),
                        website, email, phone, hours));
            }

            return seen.values().stream()
                    .sorted(Comparator
                            .comparing(VendorResponse::getDistanceMeters, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(VendorResponse::getName, String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_RESULTS)
                    .map(this::copyVendor)
                    .toList();
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
            if (houseNumber != null) {
                sb.append(" ").append(houseNumber);
            }
        }
        if (city != null) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            if (postcode != null) {
                sb.append(postcode).append(" ");
            }
            sb.append(city);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String determineCategory(JsonNode tags) {
        String craft = getTag(tags, "craft");
        if (craft != null) {
            return capitalise(craft);
        }

        String shop = getTag(tags, "shop");
        if (shop != null) {
            return capitalise(shop);
        }

        String office = getTag(tags, "office");
        if (office != null) {
            return capitalise(office);
        }

        String amenity = getTag(tags, "amenity");
        if (amenity != null) {
            return capitalise(amenity);
        }

        String tourism = getTag(tags, "tourism");
        if (tourism != null) {
            return capitalise(tourism);
        }

        return "Vendor";
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1).replace('_', ' ');
    }

    private void mergeVendors(Map<Long, VendorAccumulator> merged, List<VendorResponse> results) {
        for (VendorResponse vendor : results) {
            merged.computeIfAbsent(vendor.getOsmId(), ignored -> new VendorAccumulator(vendor))
                    .merge(vendor);
        }
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

    private String buildSearchCacheKey(String city, int radiusMeters, List<String> optionNames, String countryCode) {
        String joinedOptions = optionNames.stream()
                .map(option -> option == null ? "" : option.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return String.join("|",
                city == null ? "" : city.trim().toLowerCase(Locale.ROOT),
                String.valueOf(radiusMeters),
                joinedOptions,
                countryCode == null ? "" : countryCode);
    }

    private List<VendorResponse> getCachedList(String key) {
        CacheEntry<List<VendorResponse>> entry = searchCache.get(key);
        if (!isValid(entry)) {
            searchCache.remove(key);
            return null;
        }
        return copyVendorList(entry.value);
    }

    private void putCachedList(String key, List<VendorResponse> results) {
        searchCache.put(key, new CacheEntry<>(copyVendorList(results), System.currentTimeMillis() + SEARCH_CACHE_TTL_MS));
    }

    private boolean isValid(CacheEntry<?> entry) {
        return entry != null && entry.expiresAtMillis > System.currentTimeMillis();
    }

    private List<VendorResponse> copyVendorList(List<VendorResponse> vendors) {
        return vendors.stream().map(this::copyVendor).toList();
    }

    private VendorResponse copyVendor(VendorResponse vendor) {
        return new VendorResponse(
                vendor.getOsmId(),
                vendor.getName(),
                vendor.getAddress(),
                vendor.getLat(),
                vendor.getLon(),
                vendor.getDistanceMeters(),
                vendor.getCategory(),
                vendor.getOptionName(),
                vendor.getMatchedOptions() == null ? null : new ArrayList<>(vendor.getMatchedOptions()),
                vendor.getWebsite(),
                vendor.getEmail(),
                vendor.getPhone(),
                vendor.getOpeningHours()
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

    private static final class VendorAccumulator {
        private final Long osmId;
        private final String name;
        private final String address;
        private final Double lat;
        private final Double lon;
        private Integer distanceMeters;
        private final String category;
        private final String website;
        private final String email;
        private final String phone;
        private final String openingHours;
        private final LinkedHashSet<String> matchedOptions = new LinkedHashSet<>();
        private String primaryOptionName;

        private VendorAccumulator(VendorResponse seed) {
            this.osmId = seed.getOsmId();
            this.name = seed.getName();
            this.address = seed.getAddress();
            this.lat = seed.getLat();
            this.lon = seed.getLon();
            this.distanceMeters = seed.getDistanceMeters();
            this.category = seed.getCategory();
            this.website = seed.getWebsite();
            this.email = seed.getEmail();
            this.phone = seed.getPhone();
            this.openingHours = seed.getOpeningHours();
            this.primaryOptionName = seed.getOptionName();
            if (seed.getMatchedOptions() != null) {
                this.matchedOptions.addAll(seed.getMatchedOptions());
            } else if (seed.getOptionName() != null) {
                this.matchedOptions.add(seed.getOptionName());
            }
        }

        private VendorAccumulator merge(VendorResponse vendor) {
            if (vendor.getDistanceMeters() != null
                    && (distanceMeters == null || vendor.getDistanceMeters() < distanceMeters)) {
                distanceMeters = vendor.getDistanceMeters();
            }
            if (vendor.getMatchedOptions() != null) {
                matchedOptions.addAll(vendor.getMatchedOptions());
            } else if (vendor.getOptionName() != null) {
                matchedOptions.add(vendor.getOptionName());
            }
            if (primaryOptionName == null && vendor.getOptionName() != null) {
                primaryOptionName = vendor.getOptionName();
            }
            return this;
        }

        private VendorResponse toResponse() {
            List<String> options = new ArrayList<>(matchedOptions);
            String optionName = primaryOptionName != null ? primaryOptionName : options.stream().findFirst().orElse(null);
            return new VendorResponse(
                    osmId, name, address, lat, lon, distanceMeters,
                    category, optionName, options, website, email, phone, openingHours
            );
        }
    }
}
