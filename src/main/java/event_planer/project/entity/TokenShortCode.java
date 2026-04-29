package event_planer.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Maps invite tokens to short codes (e.g. "abc123") for WAF-safe sharing.
 * When an organiser/admin requests a share URL, we generate or return an existing
 * short code. Recipients can then open /s/{shortCode} which resolves the token
 * and renders the invite page without triggering WAF rules on UUID patterns.
 */
@Entity
@Table(name = "token_short_codes", uniqueConstraints = {
        @UniqueConstraint(columnNames = "short_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenShortCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The invite token (UUID format) */
    @Column(nullable = false, length = 36)
    private String inviteToken;

    /** Short human-readable code (e.g. "abc123xyz") */
    @Column(nullable = false, length = 20)
    private String shortCode;

    /** When this mapping was created */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last accessed time (for optional cleanup) */
    private LocalDateTime lastAccessedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
