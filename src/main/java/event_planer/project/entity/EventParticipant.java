package event_planer.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "event_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipant {

    @EmbeddedId
    private EventParticipantId id;

    // @MapsId links the "eventId" field inside EventParticipantId to this FK column
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    // @MapsId links the "userId" field inside EventParticipantId to this FK column
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Optional display name for the participant.
     * For guests: usually "Alice" or "Alice Smith"
     * For registered users: can be used if event has collectParticipantNames = true
     * If null and user is registered: organiser sees their email instead
     */
    @Column(name = "participant_name", length = 100)
    private String participantName;

    // Automatically records when the user joined the event
    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
