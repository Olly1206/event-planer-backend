package event_planer.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A vendor/service-provider suggestion fetched from OpenStreetMap via Overpass.
 * e.g. a caterer, photographer, DJ, florist, security company etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorResponse {
    private Long osmId;
    private String name;
    private String address;
    private Double lat;
    private Double lon;
    /** The OSM-derived category, e.g. "Caterer", "Photographer" */
    private String category;
    /** Which EventOption this vendor maps to, e.g. "Catering", "Photography" */
    private String optionName;
    private String website;
    private String phone;
    private String openingHours;
}
