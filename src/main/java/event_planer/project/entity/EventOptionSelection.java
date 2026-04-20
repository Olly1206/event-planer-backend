package event_planer.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_option_selections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventOptionSelection {

    @EmbeddedId
    private EventOptionSelectionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("optionId")
    @JoinColumn(name = "option_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EventOption option;

    // Optional free-text note per selection, e.g. "Vegetarian catering for 20 guests"
    @Column(columnDefinition = "TEXT")
    private String notes;
}
