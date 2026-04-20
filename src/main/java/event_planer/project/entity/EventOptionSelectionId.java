package event_planer.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for the event_option_selections table.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventOptionSelectionId implements Serializable {

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "option_id")
    private Long optionId;
}
