package event_planer.project.dto.event;

import java.time.LocalDateTime;
import java.util.Set;

import event_planer.project.entity.Event;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateEventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    private LocalDateTime eventDate;

    /** End date/time of the event — optional, must be after eventDate if provided. */
    private LocalDateTime eventEndDate;

    @Size(max = 200, message = "Location name must not exceed 200 characters")
    private String locationName;

    private Event.LocationType locationType;

    private Event.Visibility visibility;

    @Min(value = 1, message = "Must allow at least 1 participant")
    private Integer maxParticipants;

    // Which category this event belongs to (e.g. Seminar, Party)
    private Long eventTypeId;

    // IDs of any options selected for this event (e.g. Catering, AV Equipment)
    private Set<Long> optionIds;

    /**
     * If true, when participants join, they provide their display name.
     * Organiser can see "Alice (alice@example.com)" instead of just email.
     * Defaults to false.
     */
    private Boolean collectParticipantNames;
}
