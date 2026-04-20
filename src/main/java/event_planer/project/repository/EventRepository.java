package event_planer.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import event_planer.project.entity.Event;
import event_planer.project.entity.User;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOrganiser(User organiser);
    List<Event> findByOrganiserId(Long organiserId);
    List<Event> findByStatus(Event.Status status);
    List<Event> findByEventTypeId(Long eventTypeId);

    Optional<Event> findByInviteToken(String inviteToken);

    /** Events the given user has joined as a participant (not as organiser). */
    @Query("SELECT ep.event FROM EventParticipant ep WHERE ep.user.id = :userId")
    List<Event> findJoinedByUserId(Long userId);

    @Query("SELECT e FROM Event e WHERE LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Event> searchByTitle(String keyword);
}
