package event_planer.project.repository;

import event_planer.project.entity.EventOptionSelection;
import event_planer.project.entity.EventOptionSelectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventOptionSelectionRepository extends JpaRepository<EventOptionSelection, EventOptionSelectionId> {

    // Remove all option selections for an event (used when updating event options)
    // @Modifying marks this as a write operation — required for DELETE/UPDATE @Query methods
    @Modifying
    @Query("DELETE FROM EventOptionSelection s WHERE s.id.eventId = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
