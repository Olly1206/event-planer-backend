package event_planer.project.dto.event;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventParticipantResponse {

    private Long userId;
    private String username;
    private String email;
    private String participantName;
    private LocalDateTime joinedAt;
}
