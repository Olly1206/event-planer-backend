package event_planer.project.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import event_planer.project.dto.auth.AuthResponse;
import event_planer.project.dto.auth.GuestAuthResponse;
import event_planer.project.dto.auth.LoginRequest;
import event_planer.project.dto.auth.RegisterRequest;
import event_planer.project.dto.user.UserResponse;
import event_planer.project.entity.Event;
import event_planer.project.entity.EventParticipant;
import event_planer.project.entity.EventParticipantId;
import event_planer.project.entity.User;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.repository.EventParticipantRepository;
import event_planer.project.repository.EventRepository;
import event_planer.project.repository.UserRepository;
import event_planer.project.security.JwtService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String GUEST_EMAIL_DOMAIN = "guest.eventplanner.local";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;

    /**
     * Registers a new user account.
     * Validates that the email and username are not already taken,
     * bcrypt-hashes the password, then persists the new user.
     *
     * If deviceUuid is provided (guest upgrade), merges all guest events to this new account.
     *
     * Returns an AuthResponse containing a signed JWT token and user info.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateRegistrableRole(request.getRole());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                // Hash the plaintext password — NEVER store it raw
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        User saved = userRepository.save(user);

        // If guest device UUID provided, merge guest events to this new account
        if (request.getDeviceUuid() != null && !request.getDeviceUuid().isBlank()) {
            mergeGuestEvents(request.getDeviceUuid(), saved.getId());
        }

        return new AuthResponse(
                jwtService.generateToken(saved.getId(), saved.getRole().name()),
                saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRole());
    }

    /**
     * Validates login credentials.
     * Looks up the user by email, then uses BCrypt to compare the submitted
     * password against the stored hash. Never compares plaintext directly.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found with that email"));

        if (user.getRole() == User.Role.GUEST || user.getPasswordHash() == null) {
            throw new IllegalArgumentException("Guest accounts cannot log in with email and password");
        }

        // passwordEncoder.matches() hashes the raw input and compares — no plaintext stored
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password");
        }

        return new AuthResponse(
                jwtService.generateToken(user.getId(), user.getRole().name()),
                user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    /**
     * Returns a safe public view of a user by ID.
     * Throws 404 if the user does not exist.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToResponse(user);
    }

    /**
     * Deletes the current user's account and account-owned data.
     *
     * Organised events are removed through the User -> Event cascade. Joined-event
     * participations are removed through the User -> EventParticipant cascade.
     * Admin grants on events owned by other users must be removed explicitly
     * because they live in a many-to-many join table.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        List<Event> administeredEvents = eventRepository.findAdministeredByUserId(userId);
        for (Event event : administeredEvents) {
            event.getAdmins().removeIf(admin -> admin.getId().equals(userId));
        }

        userRepository.delete(user);
    }

    // ── Guest Mode ──────────────────────────────────────────────────────────────

    /**
     * Creates a temporary guest user without email or password.
     * Guest expires after 30 days and can be linked to a registered account later.
     *
     * @return GuestAuthResponse with JWT token, device UUID, and expiry time
     */
    @Transactional
    public GuestAuthResponse createGuestUser() {
        String deviceUuid = UUID.randomUUID().toString();
        String guestSecret = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(30);

        User guest = User.builder()
                .username("guest_" + deviceUuid.substring(0, 8))  // Unique temporary username
                // Internal-only placeholder credentials keep DB constraints satisfied
                // while still preventing normal email/password login for guests.
                .email("guest_" + deviceUuid.substring(0, 8) + "@" + GUEST_EMAIL_DOMAIN)
                .passwordHash(passwordEncoder.encode(guestSecret))
                .role(User.Role.GUEST)
                .deviceUuid(deviceUuid)
                .expiresAt(expiresAt)
                .build();

        User saved = userRepository.save(guest);
        String token = jwtService.generateToken(saved.getId(), User.Role.GUEST.name());

        return new GuestAuthResponse(
                saved.getId(),
                token,
                deviceUuid,
                expiresAt.toString(),
                "Welcome! Guest mode active for 30 days. Sign up anytime to keep your events."
        );
    }

    /**
     * Merges all events created/joined by a guest into a newly registered user account.
     * Called after a guest signs up with their device UUID.
     *
     * Changes event.organiser from guest to registered user (if guest was organiser)
     * Changes EventParticipant.user from guest to registered user (if guest was participant)
     * Deletes the guest user account after migration completes.
     *
     * @param deviceUuid UUID of the guest account to migrate from
     * @param newUserId ID of the new registered user
     */
    @Transactional
    public void mergeGuestEvents(String deviceUuid, Long newUserId) {
        User guestUser = userRepository.findByDeviceUuid(deviceUuid)
                .orElse(null);  // If guest not found, silently skip (already merged or expired)

        if (guestUser == null) {
            return;
        }

        User newUser = userRepository.findById(newUserId)
                .orElseThrow(() -> new ResourceNotFoundException("New user not found: " + newUserId));

        // Migrate events where guest was organiser
        List<Event> guestOrganisedEvents = eventRepository.findByOrganiserId(guestUser.getId());
        for (Event event : guestOrganisedEvents) {
            event.setOrganiser(newUser);
            eventRepository.save(event);
        }

        // Migrate event participations where guest was participant
        List<EventParticipant> guestParticipations = eventParticipantRepository.findByUserId(guestUser.getId());
        for (EventParticipant participation : guestParticipations) {
            Long eventId = participation.getEvent().getId();
            boolean alreadyJoined = eventParticipantRepository.existsByEventIdAndUserId(eventId, newUserId);

            eventParticipantRepository.delete(participation);
            if (alreadyJoined) {
                continue;
            }

            EventParticipant migratedParticipation = EventParticipant.builder()
                    .id(new EventParticipantId(eventId, newUserId))
                    .event(participation.getEvent())
                    .user(newUser)
                    .participantName(participation.getParticipantName())
                    .joinedAt(participation.getJoinedAt())
                    .build();
            eventParticipantRepository.save(migratedParticipation);
        }

        // Delete the guest user account (cascade will remove any remaining orphaned records)
        userRepository.delete(guestUser);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Scheduled task that runs daily at midnight to delete expired guest accounts.
     * Guest accounts expire after 30 days of inactivity/creation.
     * This helps keep the database clean and reduces clutter.
     *
     * Run time: Every day at 00:00 (midnight)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredGuests() {
        LocalDateTime now = LocalDateTime.now();
        List<User> expiredGuests = userRepository.findByRoleAndExpiresAtBefore(User.Role.GUEST, now);
        
        if (!expiredGuests.isEmpty()) {
            userRepository.deleteAll(expiredGuests);
            // Note: cascade rules in Event and EventParticipant will handle cleanup
        }
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    /**
     * Converts a User entity to a UserResponse DTO.
     * This is the only place where the translation lives — keeps it consistent.
     */
    public UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    private void validateRegistrableRole(User.Role role) {
        if (role == User.Role.ADMIN || role == User.Role.GUEST) {
            throw new IllegalArgumentException("Only PRIVATE or COMPANY accounts can be registered here");
        }
    }
}
