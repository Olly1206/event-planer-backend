package event_planer.project.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import event_planer.project.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Used during login — look up a user by their email address
    Optional<User> findByEmail(String email);

    // Used during registration — check if username is already taken
    boolean existsByUsername(String username);

    // Used during registration — check if email is already taken
    boolean existsByEmail(String email);

    // Used when adding an admin to an event — look up user by their chosen username
    Optional<User> findByUsername(String username);

    // Guest mode: look up guest user by device UUID for merging events
    Optional<User> findByDeviceUuid(String deviceUuid);

    // Maintenance: find all expired guest accounts for cleanup
    List<User> findByRoleAndExpiresAtBefore(User.Role role, LocalDateTime expiresAt);
}
