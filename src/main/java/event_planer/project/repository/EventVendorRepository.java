package event_planer.project.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import event_planer.project.entity.EventVendor;

public interface EventVendorRepository extends JpaRepository<EventVendor, Long> {
    boolean existsByEventIdAndOsmId(Long eventId, Long osmId);
    Optional<EventVendor> findByEventIdAndOsmId(Long eventId, Long osmId);
}
