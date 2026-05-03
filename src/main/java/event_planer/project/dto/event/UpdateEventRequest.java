package event_planer.project.dto.event;

import java.time.LocalDateTime;
import java.util.Set;

import event_planer.project.entity.Event;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Used for PATCH/PUT requests — every field is optional so the client
 * can update just the fields it sends (nulls are ignored in the service layer).
 */
@Data
public class UpdateEventRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;

    private LocalDateTime eventEndDate;

    @Size(max = 200)
    private String locationName;

    // Optional selected venue details (from /api/venues)
    private Long venueOsmId;

    @Size(max = 200)
    private String venueName;

    @Size(max = 1000)
    private String venueAddress;

    private Double venueLat;
    private Double venueLon;

    @Size(max = 120)
    private String venueCategory;

    @Size(max = 255)
    private String venueWebsite;

    @Size(max = 80)
    private String venuePhone;

    @Size(max = 255)
    private String venueOpeningHours;

    private Event.LocationType locationType;
    private Event.Visibility visibility;
    private Event.Status status;

    @Min(value = 1)
    private Integer maxParticipants;

    private Long eventTypeId;
    private Set<Long> optionIds;

    /**
     * Toggle whether participant names should be collected when joining.
     */
    private Boolean collectParticipantNames;
}
