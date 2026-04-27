package event_planer.project.service;

import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import event_planer.project.dto.NamedItemResponse;
import event_planer.project.dto.VendorResponse;
import event_planer.project.dto.event.CreateEventRequest;
import event_planer.project.dto.event.EventResponse;
import event_planer.project.dto.event.EventVendorRequest;
import event_planer.project.dto.event.UpdateEventRequest;
import event_planer.project.entity.Event;
import event_planer.project.entity.EventOption;
import event_planer.project.entity.EventOptionSelection;
import event_planer.project.entity.EventOptionSelectionId;
import event_planer.project.entity.EventParticipant;
import event_planer.project.entity.EventParticipantId;
import event_planer.project.entity.EventType;
import event_planer.project.entity.EventVendor;
import event_planer.project.entity.User;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.repository.EventOptionRepository;
import event_planer.project.repository.EventOptionSelectionRepository;
import event_planer.project.repository.EventParticipantRepository;
import event_planer.project.repository.EventRepository;
import event_planer.project.repository.EventTypeRepository;
import event_planer.project.repository.EventVendorRepository;
import event_planer.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventOptionRepository eventOptionRepository;
    private final EventOptionSelectionRepository eventOptionSelectionRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventVendorRepository eventVendorRepository;

    // ── Create ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new event owned by the given organiser.
     * @Transactional ensures the event + all its option selections are saved
     * atomically — if any part fails, the whole thing rolls back.
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, Long organiserId) {
        User organiser = userRepository.findById(organiserId)
                .orElseThrow(() -> new ResourceNotFoundException("Organiser not found: " + organiserId));

        // Look up the event type if one was provided
        EventType eventType = null;
        if (request.getEventTypeId() != null) {
            eventType = eventTypeRepository.findById(request.getEventTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("EventType not found: " + request.getEventTypeId()));
        }

        // Build and save the core event first — we need its generated ID before
        // we can create the option-selection rows that FK back to it
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .eventDate(request.getEventDate())
                .eventEndDate(request.getEventEndDate())
                .locationName(request.getLocationName())
                .locationType(request.getLocationType())
                .visibility(request.getVisibility() != null
                        ? request.getVisibility() : Event.Visibility.PUBLIC)
                .collectParticipantNames(request.getCollectParticipantNames() != null
                        ? request.getCollectParticipantNames() : false)
                .maxParticipants(request.getMaxParticipants())
                .organiser(organiser)
                .eventType(eventType)
                .inviteToken(UUID.randomUUID().toString())
                .build();

        Event saved = eventRepository.save(event);

        // Now attach any selected options (e.g. Catering, AV Equipment)
        if (request.getOptionIds() != null && !request.getOptionIds().isEmpty()) {
            attachOptions(saved, request.getOptionIds());
        }

        return mapToResponse(saved, organiserId);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id, Long requestingUserId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));

        // Private events are only visible to the organiser, admins, and participants
        if (event.getVisibility() == Event.Visibility.PRIVATE) {
            boolean isOrganiser = requestingUserId != null
                    && event.getOrganiser().getId().equals(requestingUserId);
            boolean isAdmin = requestingUserId != null
                    && event.getAdmins().stream().anyMatch(u -> u.getId().equals(requestingUserId));
            boolean isParticipant = requestingUserId != null
                    && eventParticipantRepository.existsByEventIdAndUserId(id, requestingUserId);
            if (!isOrganiser && !isAdmin && !isParticipant) {
                throw new ResourceNotFoundException("Event not found: " + id);
            }
        }

        return mapToResponse(event, requestingUserId);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .filter(e -> e.getVisibility() != Event.Visibility.PRIVATE)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByOrganiser(Long organiserId, Long requestingUserId) {
        return eventRepository.findByOrganiserId(organiserId)
                .stream()
                // Only the organiser themselves can see their own private events
                .filter(e -> e.getVisibility() != Event.Visibility.PRIVATE
                        || organiserId.equals(requestingUserId))
                .map(e -> mapToResponse(e, requestingUserId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventResponse> searchEvents(String keyword) {
        return eventRepository.searchByTitle(keyword)
                .stream()
                .filter(e -> e.getVisibility() != Event.Visibility.PRIVATE)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    /**
     * Partially updates an event — only non-null fields in the request are applied.
     * Ownership is checked here: only the organiser may update their own event.
     */
    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        // Authorisation check — only the organiser can edit their own event
        if (!event.getOrganiser().getId().equals(requestingUserId)) {
            throw new SecurityException("You do not have permission to edit this event");
        }

        // Only update fields that were actually sent (null = leave unchanged)
        if (request.getTitle() != null)                   event.setTitle(request.getTitle());
        if (request.getDescription() != null)             event.setDescription(request.getDescription());
        if (request.getEventDate() != null)               event.setEventDate(request.getEventDate());
        if (request.getEventEndDate() != null)            event.setEventEndDate(request.getEventEndDate());
        if (request.getLocationName() != null)            event.setLocationName(request.getLocationName());
        if (request.getLocationType() != null)            event.setLocationType(request.getLocationType());
        if (request.getVisibility() != null)              event.setVisibility(request.getVisibility());
        if (request.getStatus() != null)                  event.setStatus(request.getStatus());
        if (request.getMaxParticipants() != null)         event.setMaxParticipants(request.getMaxParticipants());
        if (request.getCollectParticipantNames() != null) event.setCollectParticipantNames(request.getCollectParticipantNames());

        if (request.getEventTypeId() != null) {
            EventType eventType = eventTypeRepository.findById(request.getEventTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("EventType not found: " + request.getEventTypeId()));
            event.setEventType(eventType);
        }

        // If new options were provided, replace all existing option selections
        if (request.getOptionIds() != null) {
            eventOptionSelectionRepository.deleteByEventId(eventId);
            attachOptions(event, request.getOptionIds());
        }

        return mapToResponse(eventRepository.save(event));
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /**
     * Deletes an event. Only the organiser may delete their own event.
     * cascade = CascadeType.ALL on the entity means JPA automatically removes
     * all participant and option-selection rows too.
     */
    @Transactional
    public void deleteEvent(Long eventId, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getOrganiser().getId().equals(requestingUserId)) {
            throw new SecurityException("You do not have permission to delete this event");
        }

        eventRepository.delete(event);
    }

    // ── Participants ───────────────────────────────────────────────────────────

    /**
     * Adds a user as a participant to an event.
     * Checks: event exists, user exists, not already joined, not over capacity.
     */
    @Transactional
    public void joinEvent(Long eventId, Long userId) {
        joinEventWithName(eventId, userId, null);
    }

    /**
     * Adds a user as a participant to an event with optional participant name.
     * If the event has collectParticipantNames = true, the name is stored and displayed to organiser.
     * If null or event doesn't collect names, only email is visible.
     *
     * @param eventId id of the event
     * @param userId id of the joining user
     * @param participantName optional display name (e.g. "Alice" or "Alice Smith")
     */
    @Transactional
    public void joinEventWithName(Long eventId, Long userId, String participantName) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (eventParticipantRepository.existsByEventIdAndUserId(eventId, userId)) {
            throw new IllegalStateException("User is already a participant of this event");
        }

        if (event.getMaxParticipants() != null) {
            int current = eventParticipantRepository.findByEventId(eventId).size();
            if (current >= event.getMaxParticipants()) {
                throw new IllegalStateException("This event is already full");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        EventParticipant participant = EventParticipant.builder()
                .id(new EventParticipantId(eventId, userId))
                .event(event)
                .user(user)
                .participantName(participantName)
                .build();

        eventParticipantRepository.save(participant);
    }

    /**
     * Removes a user from an event's participant list.
     */
    @Transactional
    public void leaveEvent(Long eventId, Long userId) {
        EventParticipantId participantId = new EventParticipantId(eventId, userId);
        if (!eventParticipantRepository.existsById(participantId)) {
            throw new ResourceNotFoundException("User is not a participant of this event");
        }
        eventParticipantRepository.deleteById(participantId);
    }

    // ── Event vendors ─────────────────────────────────────────────────────────

    @Transactional
    public VendorResponse addVendorToEvent(Long eventId, EventVendorRequest request, Long requestingUserId) {
        Event event = requireManageableEvent(eventId, requestingUserId);

        if (eventVendorRepository.existsByEventIdAndOsmId(eventId, request.getOsmId())) {
            throw new IllegalStateException("This vendor has already been added to the event");
        }

        EventVendor vendor = EventVendor.builder()
                .event(event)
                .osmId(request.getOsmId())
                .name(request.getName())
                .address(request.getAddress())
                .category(request.getCategory())
                .optionName(request.getOptionName())
                .website(request.getWebsite())
                .email(request.getEmail())
                .phone(request.getPhone())
                .build();

        EventVendor saved = eventVendorRepository.save(vendor);
        event.getVendors().add(saved);
        return mapEventVendor(saved);
    }

    @Transactional
    public void removeVendorFromEvent(Long eventId, Long osmId, Long requestingUserId) {
        Event event = requireManageableEvent(eventId, requestingUserId);

        EventVendor vendor = eventVendorRepository.findByEventIdAndOsmId(eventId, osmId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found on this event"));

        event.getVendors().remove(vendor);
        eventVendorRepository.delete(vendor);
    }

    // ── Reference data ─────────────────────────────────────────────────────────

    // ── Invite link ────────────────────────────────────────────────────────────

    /**
     * Returns the invite token for the event.
     * Only the organiser or an admin may retrieve it.
     */
    @Transactional(readOnly = true)
    public String getInviteToken(Long eventId, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
        if (!event.getOrganiser().getId().equals(requestingUserId)
                && event.getAdmins().stream().noneMatch(u -> u.getId().equals(requestingUserId))) {
            throw new SecurityException("Not authorised to view the invite link for this event");
        }
        return event.getInviteToken();
    }

    /**
     * Returns a public preview of an event given its invite token.
     * No authentication required — anyone with the link can see the preview.
     */
    @Transactional(readOnly = true)
    public EventResponse previewByToken(String token) {
        Event event = eventRepository.findByInviteToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite link"));
        return mapToResponse(event, null);
    }

    /**
     * Joins the event identified by the invite token as the requesting user.
     * The user is added as a regular participant.
     */
    @Transactional
    public void joinByToken(String token, Long userId) {
        joinByTokenWithName(token, userId, null);
    }

    /**
     * Joins the event identified by the invite token with optional participant name.
     * If the event has collectParticipantNames = true, the name is stored and displayed to organiser.
     *
     * @param token the invite token
     * @param userId id of the joining user
     * @param participantName optional display name (e.g. "Alice" or "Alice Smith")
     */
    @Transactional
    public void joinByTokenWithName(String token, Long userId, String participantName) {
        Event event = eventRepository.findByInviteTokenForUpdate(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite link"));

        if (eventParticipantRepository.existsByEventIdAndUserId(event.getId(), userId)) {
            throw new IllegalStateException("You have already joined this event");
        }

        // Enforce capacity limit — invite links must not bypass this
        if (event.getMaxParticipants() != null) {
            int current = eventParticipantRepository.findByEventId(event.getId()).size();
            if (current >= event.getMaxParticipants()) {
                throw new IllegalStateException("This event is already full");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        EventParticipant participant = EventParticipant.builder()
                .id(new EventParticipantId(event.getId(), userId))
                .event(event)
                .user(user)
                .participantName(participantName)
                .build();
        eventParticipantRepository.save(participant);
    }

    // ── Admin management ──────────────────────────────────────────────────────

    /**
     * Grants admin rights on an event to another user (identified by username).
     * Only the organiser may do this.
     */
    @Transactional
    public void addAdmin(Long eventId, String username, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getOrganiser().getId().equals(requestingUserId)) {
            throw new SecurityException("Only the organiser can manage admins");
        }

        User newAdmin = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        event.getAdmins().add(newAdmin);
        eventRepository.save(event);
    }

    /**
     * Revokes admin rights from a user on an event.
     * Only the organiser may do this.
     */
    @Transactional
    public void removeAdmin(Long eventId, Long adminUserId, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getOrganiser().getId().equals(requestingUserId)) {
            throw new SecurityException("Only the organiser can manage admins");
        }

        event.getAdmins().removeIf(u -> u.getId().equals(adminUserId));
        eventRepository.save(event);
    }

    /**
     * Returns all events the requesting user has joined as a participant.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getJoinedEvents(Long userId) {
        return eventRepository.findJoinedByUserId(userId)
                .stream()
                .map(e -> mapToResponse(e, userId))
                .collect(Collectors.toList());
    }

    /** Returns all events created by the organiser (existing method, kept for the filter chip). */
    @Transactional(readOnly = true)
    public List<EventResponse> getCreatedEvents(Long organiserId) {
        return eventRepository.findByOrganiserId(organiserId)
                .stream()
                .map(e -> mapToResponse(e, organiserId))
                .collect(Collectors.toList());
    }

    /** Returns all event types (id + name) for client-side ID lookup. */
    @Transactional(readOnly = true)
    public List<NamedItemResponse> getAllEventTypes() {
        return eventTypeRepository.findAll()
                .stream()
                .map(t -> new NamedItemResponse(t.getId(), t.getName()))
                .collect(Collectors.toList());
    }

    /** Returns all event options (id + name) for client-side ID lookup. */
    @Transactional(readOnly = true)
    public List<NamedItemResponse> getAllEventOptions() {
        return eventOptionRepository.findAll()
                .stream()
                .map(o -> new NamedItemResponse(o.getId(), o.getName()))
                .collect(Collectors.toList());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Looks up each option ID, validates it exists, then creates and saves
     * an EventOptionSelection row for each one.
     */
    private void attachOptions(Event event, Set<Long> optionIds) {
        Set<EventOptionSelection> selections = new HashSet<>();
        for (Long optionId : optionIds) {
            EventOption option = eventOptionRepository.findById(optionId)
                    .orElseThrow(() -> new ResourceNotFoundException("EventOption not found: " + optionId));

            EventOptionSelection selection = EventOptionSelection.builder()
                    .id(new EventOptionSelectionId(event.getId(), optionId))
                    .event(event)
                    .option(option)
                    .build();

            selections.add(selection);
        }
        eventOptionSelectionRepository.saveAll(selections);
        // Keep the in-memory entity in sync so mapToResponse sees the options immediately
        event.getOptionSelections().addAll(selections);
    }

    private Event requireManageableEvent(Long eventId, Long requestingUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        boolean isOrganiser = event.getOrganiser().getId().equals(requestingUserId);
        boolean isAdmin = event.getAdmins().stream().anyMatch(u -> u.getId().equals(requestingUserId));
        if (!isOrganiser && !isAdmin) {
            throw new SecurityException("You do not have permission to manage vendors for this event");
        }

        return event;
    }

    private VendorResponse mapEventVendor(EventVendor vendor) {
        return new VendorResponse(
                vendor.getOsmId(),
                vendor.getName(),
                vendor.getAddress(),
                null,
                null,
                null,
                vendor.getCategory(),
                vendor.getOptionName(),
                vendor.getOptionName() == null ? List.of() : List.of(vendor.getOptionName()),
                vendor.getWebsite(),
                vendor.getEmail(),
                vendor.getPhone(),
                null
        );
    }

    /**
     * Maps an Event entity to an EventResponse DTO.
     * Pass {@code requestingUserId} to populate invite token and isAdmin flag;
     * pass {@code null} for the public token-preview endpoint.
     */
    private EventResponse mapToResponse(Event event, Long requestingUserId) {
        EventResponse response = new EventResponse();
        response.setId(event.getId());
        response.setTitle(event.getTitle());
        response.setDescription(event.getDescription());
        response.setEventDate(event.getEventDate());
        response.setEventEndDate(event.getEventEndDate());
        response.setLocationName(event.getLocationName());
        response.setLocationType(event.getLocationType());
        response.setStatus(event.getStatus());
        response.setVisibility(event.getVisibility());
        response.setMaxParticipants(event.getMaxParticipants());
        response.setCollectParticipantNames(event.getCollectParticipantNames());
        response.setCreatedAt(event.getCreatedAt());

        response.setOrganiserId(event.getOrganiser().getId());
        response.setOrganiserUsername(event.getOrganiser().getUsername());

        if (event.getEventType() != null) {
            response.setEventTypeName(event.getEventType().getName());
        }

        Set<String> optionNames = event.getOptionSelections()
                .stream()
                .map(sel -> sel.getOption().getName())
                .collect(Collectors.toSet());
        response.setSelectedOptions(optionNames);

        List<VendorResponse> selectedVendors = event.getVendors()
                .stream()
                .map(this::mapEventVendor)
                .sorted(Comparator.comparing(VendorResponse::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        response.setSelectedVendors(selectedVendors);

        response.setCurrentParticipantCount(event.getParticipants().size());

        // Expose the invite token only to the organiser and admins
        boolean isOrganiser = requestingUserId != null
                && event.getOrganiser().getId().equals(requestingUserId);
        boolean isAdmin = requestingUserId != null
                && event.getAdmins().stream().anyMatch(u -> u.getId().equals(requestingUserId));
        if (isOrganiser || isAdmin) {
            response.setInviteToken(event.getInviteToken());
        }
        response.setAdmin(isAdmin);

        return response;
    }

    /** Convenience overload for internal calls that don't need user-specific fields. */
    private EventResponse mapToResponse(Event event) {
        return mapToResponse(event, null);
    }
}
