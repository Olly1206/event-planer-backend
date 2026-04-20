package event_planer.project.dto.event;

import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import event_planer.project.entity.Event;
import lombok.Data;

/**
 * What the API sends back to Android when returning event data.
 * Only contains safe, displayable fields — no internal entity references.
 */
@Data
@JsonAutoDetect(
    fieldVisibility  = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime eventDate;
    private LocalDateTime eventEndDate;
    private String locationName;
    private Event.LocationType locationType;
    private Event.Status status;
    private Event.Visibility visibility;
    private Integer maxParticipants;
    private int currentParticipantCount;

    /** If true, participant names are collected and displayed to organiser. */
    private Boolean collectParticipantNames;

    // Organiser info — just enough to display, not the full User entity
    private Long organiserId;
    private String organiserUsername;

    // Event type name (e.g. "Seminar") — not the full EventType entity
    private String eventTypeName;

    // Names of selected options (e.g. ["Catering", "AV Equipment"])
    private Set<String> selectedOptions;

    /** UUID invite token — only populated for the organiser or an admin. */
    private String inviteToken;

    /** True if the requesting user is an admin of this event. */
    private boolean isAdmin;

    private LocalDateTime createdAt;
}
