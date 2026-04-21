package event_planer.project.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import event_planer.project.dto.VendorResponse;
import event_planer.project.service.VendorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Vendor / service-provider suggestions powered by OpenStreetMap Overpass.
 *
 * Single option:
 *   GET /api/vendors?city=Berlin&optionName=Catering&radiusMeters=5000&countryCode=de
 *
 * Multiple options at once (for an event that has several selected options):
 *   GET /api/vendors?city=Berlin&optionName=Catering&optionName=Photography&radiusMeters=5000
 *
 * Public endpoint — no auth required (vendor browsing is informational only).
 * Results are sorted by distance and duplicate businesses are merged when a
 * multi-option search matches the same vendor more than once.
 */
@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendors", description = "Vendor / service-provider suggestions via Overpass (public, no auth)")
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public ResponseEntity<List<VendorResponse>> getVendors(
            @RequestParam String city,
            @RequestParam List<String> optionName,
            @RequestParam(defaultValue = "5000") int radiusMeters,
            @RequestParam(required = false) String countryCode) {

        List<VendorResponse> results;
        if (optionName.size() == 1) {
            results = vendorService.getVendors(city, radiusMeters, optionName.get(0), countryCode);
        } else {
            results = vendorService.getVendorsForMultipleOptions(city, radiusMeters, optionName, countryCode);
        }
        return ResponseEntity.ok(results);
    }
}
