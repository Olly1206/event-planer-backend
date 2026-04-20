package event_planer.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reusable DTO for id/name pairs — returned for event-types and event-options
 * lookup endpoints so the Android app can map names to database IDs before
 * submitting a CreateEventRequest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NamedItemResponse {
    private Long id;
    private String name;
}
