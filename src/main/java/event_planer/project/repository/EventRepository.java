package event_planer.project.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import event_planer.project.entity.Event;
import event_planer.project.entity.User;
import jakarta.persistence.LockModeType;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOrganiser(User organiser);
    List<Event> findByOrganiserId(Long organiserId);
    List<Event> findByStatus(Event.Status status);
    List<Event> findByEventTypeId(Long eventTypeId);

    Optional<Event> findByInviteToken(String inviteToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.inviteToken = :inviteToken")
    Optional<Event> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);

    /** Events the given user has joined as a participant (not as organiser). */
    @Query("SELECT ep.event FROM EventParticipant ep WHERE ep.user.id = :userId")
    List<Event> findJoinedByUserId(Long userId);

    /** Events where the given user has been granted admin rights. */
    @Query("SELECT e FROM Event e JOIN e.admins admin WHERE admin.id = :userId")
    List<Event> findAdministeredByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT e FROM Event e
            WHERE e.organiser.id IN (
                SELECT s.organiser.id FROM UserOrganizerSubscription s
                WHERE s.subscriber.id = :subscriberId
            )
            AND e.visibility = :visibility
            AND e.eventDate >= :fromDate
            ORDER BY e.eventDate ASC
            """)
    List<Event> findFuturePublicEventsForSubscriptions(
            @Param("subscriberId") Long subscriberId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("visibility") Event.Visibility visibility);

    @Query("SELECT e FROM Event e WHERE LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Event> searchByTitle(String keyword);
}
