package event_planer.project.repository;

import event_planer.project.entity.EventParticipant;
import event_planer.project.entity.EventParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, EventParticipantId> {

    // All participants for a given event
    List<EventParticipant> findByEventId(Long eventId);

    // All events a specific user has joined
    List<EventParticipant> findByUserId(Long userId);

    // Check if a user is already a participant of a given event
    boolean existsByEventIdAndUserId(Long eventId, Long userId);
}
