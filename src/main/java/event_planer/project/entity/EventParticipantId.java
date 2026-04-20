package event_planer.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for the event_participants table.
 * JPA requires a separate Serializable class when a table has a composite PK.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantId implements Serializable {

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;
}
