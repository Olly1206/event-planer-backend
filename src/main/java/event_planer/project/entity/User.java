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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // Stored as a bcrypt hash — never the plain-text password
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Automatically set on INSERT
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Automatically updated on every UPDATE
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * For guest users: UUID of the device they joined from.
     * Null for regular registered users.
     * Used to identify and merge guest events when guest upgrades to registered account.
     */
    @Column(name = "device_uuid", length = 36, unique = true)
    private String deviceUuid;

    /**
     * For guest users: when the guest account expires (30 days after creation).
     * Null for registered users (they don't expire).
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Events this user has created/organised
    @OneToMany(mappedBy = "organiser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<Event> organisedEvents = new HashSet<>();

    // Events this user has joined as a participant
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<EventParticipant> participations = new HashSet<>();

    public enum Role {
        GUEST,    // Temporary user without email/password, expires after 30 days
        PRIVATE,  // Personal/individual user
        COMPANY,  // Business account with additional event types
        ADMIN     // Platform administrator
    }
}
