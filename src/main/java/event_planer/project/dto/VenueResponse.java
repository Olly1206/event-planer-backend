package event_planer.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueResponse {
    private Long osmId;
    private String name;
    private String address;
    private Double lat;
    private Double lon;
    private String category;
    private String website;
    private String phone;
    private String openingHours;
}
