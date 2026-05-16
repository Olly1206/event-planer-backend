package event_planer.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "user_organizer_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"subscriber_id", "organiser_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizerSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User subscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organiser_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User organiser;

    @Column(name = "notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean notificationsEnabled = true;

    @Column(name = "email_fallback_enabled", nullable = false)
    @Builder.Default
    private Boolean emailFallbackEnabled = true;

    @Column(name = "remind_before_minutes", nullable = false)
    @Builder.Default
    private Integer remindBeforeMinutes = 1440;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
