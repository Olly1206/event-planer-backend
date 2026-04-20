package event_planer.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "event_options")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // All selections of this option across events (reverse side)
    @OneToMany(mappedBy = "option", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<EventOptionSelection> selections = new HashSet<>();
}
