package event_planer.project.dto.event;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizerSubscriptionResponse {

    private Long id;
    private Long organiserId;
    private String organiserUsername;
    private Boolean notificationsEnabled;
    private Boolean emailFallbackEnabled;
    private Integer remindBeforeMinutes;
    private LocalDateTime createdAt;
}
