package event_planer.project.dto.event;

import lombok.Data;

/**
 * Request body for joining an event.
 * If the event has collectParticipantNames = true, participantName is required.
 * Otherwise, participantName is optional and ignored.
 */
@Data
public class JoinEventRequest {
    /**
     * Optional display name for the participant.
     * Used if event.collectParticipantNames = true.
     * For guests: "Alice" or "Alice Smith"
     * For registered users: overrides their email display
     */
    private String participantName;
}
