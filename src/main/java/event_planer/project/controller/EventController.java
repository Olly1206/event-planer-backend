package event_planer.project.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import event_planer.project.dto.NamedItemResponse;
import event_planer.project.dto.VendorResponse;
import event_planer.project.dto.event.CreateEventRequest;
import event_planer.project.dto.event.EventDashboardResponse;
import event_planer.project.dto.event.EventParticipantResponse;
import event_planer.project.dto.event.EventResponse;
import event_planer.project.dto.event.EventVendorRequest;
import event_planer.project.dto.event.JoinEventRequest;
import event_planer.project.dto.event.OrganizerSubscriptionResponse;
import event_planer.project.dto.event.ShortCodeResponse;
import event_planer.project.dto.event.SubscriptionPreferenceRequest;
import event_planer.project.dto.event.UpdateEventRequest;
import event_planer.project.security.SecurityUtils;
import event_planer.project.service.EventService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event CRUD, participants, invite links, and reference data")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    // ── Create ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/events
     * Header: X-User-Id: <userId>   ← temporary until Step 6 replaces this with JWT
     * Body: CreateEventRequest JSON
     *
     * Returns 201 Created with the full EventResponse body.
     */
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /**
     * GET /api/events
     * Returns all events. Accessible to any caller (no auth required yet).
     * 
     * Cache-Control: public, max-age=300 (5 minutes)
     * - Clients can cache this response for 5 minutes
     * - Shared caches (CDN, proxies) can also cache
     * - Ideal for the list of all events which doesn't change frequently
     */
    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        List<EventResponse> events = eventService.getAllEvents();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(events);
    }

    /** GET /api/events/subscribed — future public events from followed organisers. */
    @GetMapping("/subscribed")
    public ResponseEntity<List<EventResponse>> getSubscribedEvents() {
        return ResponseEntity.ok(eventService.getSubscribedEvents(SecurityUtils.getCurrentUserId()));
    }

    /** GET /api/events/subscriptions — organisers/clubs the current user follows. */
    @GetMapping("/subscriptions")
    public ResponseEntity<List<OrganizerSubscriptionResponse>> getSubscriptions() {
        return ResponseEntity.ok(eventService.getSubscriptions(SecurityUtils.getCurrentUserId()));
    }

    /** POST /api/events/subscriptions/organisers/{organiserId} — follow an organiser/club. */
    @PostMapping("/subscriptions/organisers/{organiserId}")
    public ResponseEntity<OrganizerSubscriptionResponse> subscribeToOrganiser(
            @PathVariable Long organiserId,
            @Valid @RequestBody(required = false) SubscriptionPreferenceRequest request) {
        OrganizerSubscriptionResponse response = eventService.subscribeToOrganiser(
                organiserId,
                SecurityUtils.getCurrentUserId(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** PATCH /api/events/subscriptions/organisers/{organiserId} — update reminder preferences. */
    @PatchMapping("/subscriptions/organisers/{organiserId}")
    public ResponseEntity<OrganizerSubscriptionResponse> updateSubscription(
            @PathVariable Long organiserId,
            @Valid @RequestBody SubscriptionPreferenceRequest request) {
        return ResponseEntity.ok(eventService.updateSubscriptionPreferences(
                organiserId,
                SecurityUtils.getCurrentUserId(),
                request));
    }

    /** DELETE /api/events/subscriptions/organisers/{organiserId} — unfollow an organiser/club. */
    @DeleteMapping("/subscriptions/organisers/{organiserId}")
    public ResponseEntity<Void> unsubscribeFromOrganiser(@PathVariable Long organiserId) {
        eventService.unsubscribeFromOrganiser(organiserId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/events/organiser/{organiserId}/followers/count — organiser adoption metric. */
    @GetMapping("/organiser/{organiserId}/followers/count")
    public ResponseEntity<Map<String, Long>> getOrganiserFollowerCount(@PathVariable Long organiserId) {
        return ResponseEntity.ok(Map.of("count", eventService.getFollowerCount(organiserId)));
    }

    /** GET /api/events/organiser/{organiserId}/dashboard — organiser dashboard summary. */
    @GetMapping("/organiser/{organiserId}/dashboard")
    public ResponseEntity<EventDashboardResponse> getOrganiserDashboard(@PathVariable Long organiserId) {
        return ResponseEntity.ok(eventService.getOrganiserDashboard(organiserId, SecurityUtils.getCurrentUserId()));
    }

    /** GET /api/events/organiser/{organiserId}/calendar.ics — public iCalendar export. */
    @GetMapping(value = "/organiser/{organiserId}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> exportOrganiserCalendar(@PathVariable Long organiserId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"organiser-" + organiserId + ".ics\"")
                .contentType(MediaType.parseMediaType("text/calendar"))
                .body(eventService.exportOrganiserCalendar(organiserId));
    }

    /**
     * GET /api/events/{id}
     * {id} is a path variable — Spring extracts it from the URL and passes it
     * to the method as a Long.
     * Uses getCurrentUserIdOrNull() because this endpoint is public —
     * unauthenticated users can view public events, authenticated users
     * additionally see private events they have access to.
     * 
     * Cache-Control: private, max-age=600 (10 minutes)
     * - Only the client browser can cache this (private = not shared caches)
     * - Cached for 10 minutes since individual events change less frequently
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        EventResponse event = eventService.getEventById(id, userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(event);
    }

    /**
     * GET /api/events/organiser/{organiserId}
     * Lists all events created by a specific user.
     * Private events are only included if the requester is the organiser.
     */
    @GetMapping("/organiser/{organiserId}")
    public ResponseEntity<List<EventResponse>> getEventsByOrganiser(@PathVariable Long organiserId) {
        Long requestingUserId = SecurityUtils.getCurrentUserIdOrNull();
        return ResponseEntity.ok(eventService.getEventsByOrganiser(organiserId, requestingUserId));
    }

    /**
     * GET /api/events/search?keyword=conference
     * @RequestParam maps the ?keyword= query string parameter to the method argument.
     */
    @GetMapping("/search")
    public ResponseEntity<List<EventResponse>> searchEvents(@RequestParam String keyword) {
        return ResponseEntity.ok(eventService.searchEvents(keyword));
    }

    /**
     * GET /api/events/filter
     * Optional public filters for browse/search screens.
     */
    @GetMapping("/filter")
    public ResponseEntity<List<EventResponse>> filterEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Long eventTypeId,
            @RequestParam(required = false) Long organiserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(eventService.filterEvents(keyword, city, eventTypeId, from, to, organiserId));
    }

    /** GET /api/events/{id}/calendar.ics — iCalendar export for one visible event. */
    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> exportEventCalendar(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + id + ".ics\"")
                .contentType(MediaType.parseMediaType("text/calendar"))
                .body(eventService.exportEventCalendar(id, SecurityUtils.getCurrentUserIdOrNull()));
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    /**
     * PATCH /api/events/{id}
     * Header: X-User-Id: <userId>
     * Body: UpdateEventRequest JSON (all fields optional — only send what you want to change)
     *
     * The service checks that the requesting user is the event organiser.
     * Returns 403 Forbidden if they are not.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request, SecurityUtils.getCurrentUserId()));
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /**
     * DELETE /api/events/{id}
     * Header: X-User-Id: <userId>
     *
     * 204 No Content is the standard response for a successful DELETE —
     * there's nothing meaningful to return in the body.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Participants ───────────────────────────────────────────────────────────

    /**
     * POST /api/events/{id}/join
     * Body (optional): { "participantName": "Alice Smith" }
     *
     * Registers the requesting user as a participant of the event.
     * If the event has collectParticipantNames = true, the participantName is stored and
     * displayed to the organiser. Otherwise, the user's email is displayed instead.
     * If body is empty or participantName is null, nothing is stored (existing behavior).
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<Void> joinEvent(@PathVariable Long id, @RequestBody(required = false) JoinEventRequest request) {
        String participantName = (request != null) ? request.getParticipantName() : null;
        eventService.joinEventWithName(id, SecurityUtils.getCurrentUserId(), participantName);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/events/{id}/leave
     * Header: X-User-Id: <userId>
     */
    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveEvent(@PathVariable Long id) {
        eventService.leaveEvent(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/events/{id}/participants — organiser/admin participant list. */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<EventParticipantResponse>> getParticipants(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getParticipants(id, SecurityUtils.getCurrentUserId()));
    }

    /** GET /api/events/{id}/participants.csv — organiser/admin CSV export. */
    @GetMapping(value = "/{id}/participants.csv", produces = "text/csv")
    public ResponseEntity<String> exportParticipantsCsv(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + id + "-participants.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(eventService.exportParticipantsCsv(id, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{id}/vendors")
    public ResponseEntity<VendorResponse> addVendorToEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventVendorRequest request) {
        VendorResponse addedVendor = eventService.addVendorToEvent(id, request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(addedVendor);
    }

    @DeleteMapping("/{id}/vendors/{osmId}")
    public ResponseEntity<Void> removeVendorFromEvent(
            @PathVariable Long id,
            @PathVariable Long osmId) {
        eventService.removeVendorFromEvent(id, osmId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    // ── Invite link ───────────────────────────────────────────────────────────

    /** GET /api/events/invite/{token} — public preview (no auth required) */
    @GetMapping("/invite/{token}")
    public ResponseEntity<EventResponse> previewByToken(@PathVariable String token) {
        return ResponseEntity.ok(eventService.previewByToken(token));
    }

    /** 
     * POST /api/events/invite/{token}/join — authenticated user joins via link
     * Body (optional): { "participantName": "Alice Smith" }
     */
    @PostMapping("/invite/{token}/join")
    public ResponseEntity<Void> joinByToken(@PathVariable String token, @RequestBody(required = false) JoinEventRequest request) {
        String participantName = (request != null) ? request.getParticipantName() : null;
        eventService.joinByTokenWithName(token, SecurityUtils.getCurrentUserId(), participantName);
        return ResponseEntity.ok().build();
    }

    /** GET /api/events/{id}/invite-link — returns the raw token (organiser/admin only) */
    @GetMapping("/{id}/invite-link")
    public ResponseEntity<String> getInviteLink(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.getInviteToken(id, SecurityUtils.getCurrentUserId()));
    }

    /** 
     * GET /api/events/{id}/invite-link/short — returns a WAF-safe short code (organiser/admin only)
     * This short code maps to the token and can be shared without triggering CDN filters.
     */
    @GetMapping("/{id}/invite-link/short")
    public ResponseEntity<ShortCodeResponse> getShortInviteCode(@PathVariable Long id) {
        String token = eventService.getInviteToken(id, SecurityUtils.getCurrentUserId());
        String shortCode = eventService.getShortCodeForToken(token);
        return ResponseEntity.ok(ShortCodeResponse.builder().shortCode(shortCode).build());
    }

    /** POST /api/events/{id}/admins/{username} — organiser grants admin to a user */
    @PostMapping("/{id}/admins/{username}")
    public ResponseEntity<Void> addAdmin(@PathVariable Long id, @PathVariable String username) {
        eventService.addAdmin(id, username, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok().build();
    }
    @DeleteMapping("/{id}/admins/{adminUserId}")
    public ResponseEntity<Void> removeAdmin(@PathVariable Long id, @PathVariable Long adminUserId) {
        eventService.removeAdmin(id, adminUserId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/events/joined — returns all events the current user has joined */
    @GetMapping("/joined")
    public ResponseEntity<List<EventResponse>> getJoinedEvents() {
        return ResponseEntity.ok(eventService.getJoinedEvents(SecurityUtils.getCurrentUserId()));
    }

    /** GET /api/events/joined/calendar.ics — iCalendar export for current user's joined events. */
    @GetMapping(value = "/joined/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> exportJoinedCalendar() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"joined-events.ics\"")
                .contentType(MediaType.parseMediaType("text/calendar"))
                .body(eventService.exportJoinedCalendar(userId));
    }

    /** GET /api/events/my — returns all events the current user has created */
    @GetMapping("/my")
    public ResponseEntity<List<EventResponse>> getMyEvents() {
        return ResponseEntity.ok(eventService.getCreatedEvents(SecurityUtils.getCurrentUserId()));
    }

    // ── Reference data ─────────────────────────────────────────────────────────

    /**
     * GET /api/events/types
     * Returns all event types (id + name) so Android can resolve a name to an
     * ID before submitting a CreateEventRequest.  Public — no auth needed.
     */
    @GetMapping("/types")
    public ResponseEntity<List<NamedItemResponse>> getEventTypes() {
        return ResponseEntity.ok(eventService.getAllEventTypes());
    }

    /**
     * GET /api/events/options
     * Returns all event options (id + name).  Public — no auth needed.
     */
    @GetMapping("/options")
    public ResponseEntity<List<NamedItemResponse>> getEventOptions() {
        return ResponseEntity.ok(eventService.getAllEventOptions());
    }
}
