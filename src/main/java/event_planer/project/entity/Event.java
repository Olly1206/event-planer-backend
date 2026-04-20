package event_planer.project.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_date")
    private LocalDateTime eventDate;

    /** When the event finishes — null means open-ended / single time slot. */
    @Column(name = "event_end_date")
    private LocalDateTime eventEndDate;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type")
    private LocationType locationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    // The user who created/owns this event
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organiser_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User organiser;

    // The category of event (e.g., Seminar, Party, Corporate)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EventType eventType;

    // Everyone who has joined this event
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<EventParticipant> participants = new HashSet<>();

    // Which options (catering, AV, etc.) have been selected for this event
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<EventOptionSelection> optionSelections = new HashSet<>();

    /** UUID invite token — generated on event creation, used to build share links. */
    @Column(name = "invite_token", length = 36, unique = true)
    private String inviteToken;

    /**
     * If true, organiser has enabled participant name collection.
     * When a user joins, they provide their name (used for both guests and registered users).
     * Organiser can see: "John Doe (john@example.com)" instead of just email.
     */
    @Column(name = "collect_participant_names")
    @Builder.Default
    private Boolean collectParticipantNames = false;

    /** Users granted admin rights on this event (can share the invite link). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_admins",
            joinColumns        = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<User> admins = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum LocationType {
        INDOOR,
        OUTDOOR,
        BOTH
    }

    public enum Status {
        DRAFT,      // Being set up, not visible to others
        PLANNED,    // Confirmed and open for participants
        ONGOING,    // Currently happening
        COMPLETED,  // Finished
        CANCELLED   // Called off
    }

    public enum Visibility {
        PUBLIC,     // Visible to everyone in listings and search
        PRIVATE     // Only visible to organiser, admins, and participants (or via invite link)
    }
}
