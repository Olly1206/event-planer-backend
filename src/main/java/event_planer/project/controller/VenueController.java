package event_planer.project.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import event_planer.project.dto.VenueResponse;
import event_planer.project.service.VenueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * GET /api/venues?city=Berlin&radiusMeters=5000&locationType=INDOOR&eventType=Conference
 *
 * Public endpoint — no auth required (venue browsing is informational only).
 * Proxies geo-data from Nominatim + OpenStreetMap Overpass API.
 */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@Tag(name = "Venues", description = "Venue suggestions via OpenStreetMap Overpass (public, no auth)")
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    public ResponseEntity<List<VenueResponse>> getVenues(
            @RequestParam String city,
            @RequestParam(defaultValue = "5000") int radiusMeters,
            @RequestParam(required = false) String locationType,
            @RequestParam(required = false) String eventType) {
        return ResponseEntity.ok(
                venueService.getVenues(city, radiusMeters, locationType, eventType));
    }
}
