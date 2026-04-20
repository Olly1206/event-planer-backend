package event_planer.project.repository;

import event_planer.project.entity.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventTypeRepository extends JpaRepository<EventType, Long> {

    // Look up a type by exact name, e.g. "Seminar"
    Optional<EventType> findByName(String name);
}
