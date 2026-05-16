package event_planer.project.dto.event;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDashboardResponse {

    private long followerCount;
    private int totalCreatedEvents;
    private int upcomingEvents;
    private int draftEvents;
    private int totalParticipantCount;
    private List<EventDashboardItemResponse> events;
}
