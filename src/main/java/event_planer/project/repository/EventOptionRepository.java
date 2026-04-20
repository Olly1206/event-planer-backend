package event_planer.project.repository;

import event_planer.project.entity.EventOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventOptionRepository extends JpaRepository<EventOption, Long> {

    // Look up an option by exact name, e.g. "Catering"
    Optional<EventOption> findByName(String name);
}
