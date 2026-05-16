package event_planer.project.dto.event;

import java.time.LocalDateTime;

import event_planer.project.entity.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDashboardItemResponse {

    private Long id;
    private String title;
    private LocalDateTime eventDate;
    private Event.Status status;
    private Event.Visibility visibility;
    private Integer maxParticipants;
    private int currentParticipantCount;
}
