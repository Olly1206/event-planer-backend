package event_planer.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import event_planer.project.entity.UserOrganizerSubscription;

@Repository
public interface UserOrganizerSubscriptionRepository extends JpaRepository<UserOrganizerSubscription, Long> {

    boolean existsBySubscriberIdAndOrganiserId(Long subscriberId, Long organiserId);

    Optional<UserOrganizerSubscription> findBySubscriberIdAndOrganiserId(Long subscriberId, Long organiserId);

    List<UserOrganizerSubscription> findBySubscriberId(Long subscriberId);

    long countByOrganiserId(Long organiserId);

    void deleteBySubscriberIdAndOrganiserId(Long subscriberId, Long organiserId);

    void deleteBySubscriberId(Long subscriberId);

    void deleteByOrganiserId(Long organiserId);
}
