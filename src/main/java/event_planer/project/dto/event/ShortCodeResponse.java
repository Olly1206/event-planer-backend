package event_planer.project.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing a short code for safe invite link sharing.
 * The short code maps to an invite token and avoids WAF/CDN issues with long UUIDs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortCodeResponse {
    private String shortCode;
}
