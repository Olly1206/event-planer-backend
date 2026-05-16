package event_planer.project.dto.event;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SubscriptionPreferenceRequest {

    private Boolean notificationsEnabled;

    private Boolean emailFallbackEnabled;

    @Min(value = 0, message = "Reminder time must not be negative")
    private Integer remindBeforeMinutes;
}
