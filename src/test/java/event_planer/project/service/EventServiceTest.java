package event_planer.project.service;

import event_planer.project.dto.NamedItemResponse;
import event_planer.project.dto.event.CreateEventRequest;
import event_planer.project.dto.event.EventResponse;
import event_planer.project.dto.event.UpdateEventRequest;
import event_planer.project.entity.*;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventTypeRepository eventTypeRepository;
    @Mock private EventOptionRepository eventOptionRepository;
    @Mock private EventOptionSelectionRepository eventOptionSelectionRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;

    @InjectMocks
    private EventService eventService;

    private User organiser;
    private EventType eventType;
    private Event publicEvent;
    private Event privateEvent;

    @BeforeEach
    void setUp() {
        organiser = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .role(User.Role.PRIVATE)
                .build();

        eventType = new EventType();
        eventType.setId(10L);
        eventType.setName("Seminar");

        publicEvent = Event.builder()
                .id(100L)
                .title("Public Meetup")
                .description("A public event")
                .eventDate(LocalDateTime.of(2025, 8, 1, 18, 0))
                .locationName("Berlin")
                .locationType(Event.LocationType.INDOOR)
                .status(Event.Status.PLANNED)
                .visibility(Event.Visibility.PUBLIC)
                .maxParticipants(50)
                .organiser(organiser)
                .eventType(eventType)
                .inviteToken("abc-123")
                .participants(new HashSet<>())
                .optionSelections(new HashSet<>())
                .admins(new HashSet<>())
                .build();

        privateEvent = Event.builder()
                .id(200L)
                .title("Private Party")
                .visibility(Event.Visibility.PRIVATE)
                .organiser(organiser)
                .eventType(eventType)
                .inviteToken("priv-456")
                .participants(new HashSet<>())
                .optionSelections(new HashSet<>())
                .admins(new HashSet<>())
                .build();
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Nested
    class CreateEvent {

        @Test
        void createsEventSuccessfully() {
            CreateEventRequest request = new CreateEventRequest();
            request.setTitle("Test Event");
            request.setEventTypeId(10L);
            request.setMaxParticipants(20);

            when(userRepository.findById(1L)).thenReturn(Optional.of(organiser));
            when(eventTypeRepository.findById(10L)).thenReturn(Optional.of(eventType));
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
                Event e = inv.getArgument(0);
                e.setId(999L);
                e.setParticipants(new HashSet<>());
                e.setOptionSelections(new HashSet<>());
                e.setAdmins(new HashSet<>());
                return e;
            });

            EventResponse response = eventService.createEvent(request, 1L);

            assertThat(response.getTitle()).isEqualTo("Test Event");
            assertThat(response.getOrganiserId()).isEqualTo(1L);
            verify(eventRepository).save(any(Event.class));
        }

        @Test
        void throwsWhenOrganiserNotFound() {
            CreateEventRequest request = new CreateEventRequest();
            request.setTitle("Some Event");

            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.createEvent(request, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Organiser not found");
        }

        @Test
        void defaultsToPublicVisibility() {
            CreateEventRequest request = new CreateEventRequest();
            request.setTitle("No Visibility Set");

            when(userRepository.findById(1L)).thenReturn(Optional.of(organiser));
            when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
                Event e = inv.getArgument(0);
                e.setId(999L);
                e.setParticipants(new HashSet<>());
                e.setOptionSelections(new HashSet<>());
                e.setAdmins(new HashSet<>());
                return e;
            });

            EventResponse response = eventService.createEvent(request, 1L);

            assertThat(response.getVisibility()).isEqualTo(Event.Visibility.PUBLIC);
        }
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    @Nested
    class GetEventById {

        @Test
        void returnsPublicEventForAnonymousUser() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            EventResponse response = eventService.getEventById(100L, null);

            assertThat(response.getTitle()).isEqualTo("Public Meetup");
        }

        @Test
        void returnsPrivateEventForOrganiser() {
            when(eventRepository.findById(200L)).thenReturn(Optional.of(privateEvent));

            EventResponse response = eventService.getEventById(200L, 1L);

            assertThat(response.getTitle()).isEqualTo("Private Party");
        }

        @Test
        void throwsForPrivateEventWhenAnonymous() {
            when(eventRepository.findById(200L)).thenReturn(Optional.of(privateEvent));

            assertThatThrownBy(() -> eventService.getEventById(200L, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsForPrivateEventWhenDifferentUser() {
            when(eventRepository.findById(200L)).thenReturn(Optional.of(privateEvent));

            assertThatThrownBy(() -> eventService.getEventById(200L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.getEventById(404L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetAllEvents {

        @Test
        void excludesPrivateEventsFromListing() {
            when(eventRepository.findAll()).thenReturn(List.of(publicEvent, privateEvent));

            List<EventResponse> results = eventService.getAllEvents();

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle()).isEqualTo("Public Meetup");
        }
    }

    @Nested
    class GetEventsByOrganiser {

        @Test
        void organiserSeesOwnPrivateEvents() {
            when(eventRepository.findByOrganiserId(1L)).thenReturn(List.of(publicEvent, privateEvent));

            List<EventResponse> results = eventService.getEventsByOrganiser(1L, 1L);

            assertThat(results).hasSize(2);
        }

        @Test
        void otherUserCannotSeePrivateEvents() {
            when(eventRepository.findByOrganiserId(1L)).thenReturn(List.of(publicEvent, privateEvent));

            List<EventResponse> results = eventService.getEventsByOrganiser(1L, 999L);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getVisibility()).isEqualTo(Event.Visibility.PUBLIC);
        }
    }

    @Nested
    class SearchEvents {

        @Test
        void excludesPrivateEventsFromSearch() {
            when(eventRepository.searchByTitle("event")).thenReturn(List.of(publicEvent, privateEvent));

            List<EventResponse> results = eventService.searchEvents("event");

            assertThat(results).hasSize(1);
        }
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Nested
    class UpdateEvent {

        @Test
        void updatesOnlyProvidedFields() {
            UpdateEventRequest request = new UpdateEventRequest();
            request.setTitle("Updated Title");

            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(publicEvent);

            EventResponse response = eventService.updateEvent(100L, request, 1L);

            assertThat(publicEvent.getTitle()).isEqualTo("Updated Title");
            assertThat(publicEvent.getDescription()).isEqualTo("A public event");
        }

        @Test
        void throwsWhenNonOrganiserTriesToUpdate() {
            UpdateEventRequest request = new UpdateEventRequest();
            request.setTitle("Hacked");

            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            assertThatThrownBy(() -> eventService.updateEvent(100L, request, 999L))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    @Nested
    class DeleteEvent {

        @Test
        void deletesEventAsOrganiser() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            eventService.deleteEvent(100L, 1L);

            verify(eventRepository).delete(publicEvent);
        }

        @Test
        void throwsWhenNonOrganiserTriesToDelete() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            assertThatThrownBy(() -> eventService.deleteEvent(100L, 999L))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ── Participants ───────────────────────────────────────────────────────────

    @Nested
    class JoinEvent {

        @Test
        void joinsSuccessfully() {
            User joiner = User.builder().id(2L).username("bob").build();
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(eventParticipantRepository.existsByEventIdAndUserId(100L, 2L)).thenReturn(false);
            when(eventParticipantRepository.findByEventId(100L)).thenReturn(List.of());
            when(userRepository.findById(2L)).thenReturn(Optional.of(joiner));

            eventService.joinEvent(100L, 2L);

            verify(eventParticipantRepository).save(any(EventParticipant.class));
        }

        @Test
        void throwsWhenAlreadyJoined() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(eventParticipantRepository.existsByEventIdAndUserId(100L, 2L)).thenReturn(true);

            assertThatThrownBy(() -> eventService.joinEvent(100L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already a participant");
        }

        @Test
        void throwsWhenEventIsFull() {
            publicEvent.setMaxParticipants(1);
            EventParticipant existing = new EventParticipant();

            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(eventParticipantRepository.existsByEventIdAndUserId(100L, 2L)).thenReturn(false);
            when(eventParticipantRepository.findByEventId(100L)).thenReturn(List.of(existing));

            assertThatThrownBy(() -> eventService.joinEvent(100L, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already full");
        }
    }

    @Nested
    class LeaveEvent {

        @Test
        void leavesSuccessfully() {
            EventParticipantId pid = new EventParticipantId(100L, 2L);
            when(eventParticipantRepository.existsById(pid)).thenReturn(true);

            eventService.leaveEvent(100L, 2L);

            verify(eventParticipantRepository).deleteById(pid);
        }

        @Test
        void throwsWhenNotAParticipant() {
            EventParticipantId pid = new EventParticipantId(100L, 2L);
            when(eventParticipantRepository.existsById(pid)).thenReturn(false);

            assertThatThrownBy(() -> eventService.leaveEvent(100L, 2L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Invite link ────────────────────────────────────────────────────────────

    @Nested
    class InviteLink {

        @Test
        void organiserCanGetInviteToken() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            String token = eventService.getInviteToken(100L, 1L);

            assertThat(token).isEqualTo("abc-123");
        }

        @Test
        void nonOrganiserCannotGetInviteToken() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            assertThatThrownBy(() -> eventService.getInviteToken(100L, 999L))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void previewByTokenReturnsEvent() {
            when(eventRepository.findByInviteToken("abc-123")).thenReturn(Optional.of(publicEvent));

            EventResponse response = eventService.previewByToken("abc-123");

            assertThat(response.getTitle()).isEqualTo("Public Meetup");
        }

        @Test
        void previewByTokenThrowsOnInvalidToken() {
            when(eventRepository.findByInviteToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.previewByToken("bad-token"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void joinByTokenChecksCapacity() {
            publicEvent.setMaxParticipants(1);
            EventParticipant existing = new EventParticipant();

            when(eventRepository.findByInviteToken("abc-123")).thenReturn(Optional.of(publicEvent));
            when(eventParticipantRepository.existsByEventIdAndUserId(100L, 2L)).thenReturn(false);
            when(eventParticipantRepository.findByEventId(100L)).thenReturn(List.of(existing));

            assertThatThrownBy(() -> eventService.joinByToken("abc-123", 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already full");
        }
    }

    // ── Admin management ──────────────────────────────────────────────────────

    @Nested
    class AdminManagement {

        @Test
        void organiserCanAddAdmin() {
            User newAdmin = User.builder().id(3L).username("charlie").build();
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(newAdmin));
            when(eventRepository.save(any(Event.class))).thenReturn(publicEvent);

            eventService.addAdmin(100L, "charlie", 1L);

            assertThat(publicEvent.getAdmins()).contains(newAdmin);
        }

        @Test
        void nonOrganiserCannotAddAdmin() {
            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));

            assertThatThrownBy(() -> eventService.addAdmin(100L, "charlie", 999L))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void organiserCanRemoveAdmin() {
            User admin = User.builder().id(3L).username("charlie").build();
            publicEvent.getAdmins().add(admin);

            when(eventRepository.findById(100L)).thenReturn(Optional.of(publicEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(publicEvent);

            eventService.removeAdmin(100L, 3L, 1L);

            assertThat(publicEvent.getAdmins()).doesNotContain(admin);
        }
    }

    // ── Reference data ────────────────────────────────────────────────────────

    @Nested
    class ReferenceData {

        @Test
        void returnsAllEventTypes() {
            EventType type1 = new EventType(); type1.setId(1L); type1.setName("Seminar");
            EventType type2 = new EventType(); type2.setId(2L); type2.setName("Party");
            when(eventTypeRepository.findAll()).thenReturn(List.of(type1, type2));

            List<NamedItemResponse> types = eventService.getAllEventTypes();

            assertThat(types).hasSize(2);
            assertThat(types.get(0).getName()).isEqualTo("Seminar");
        }

        @Test
        void returnsAllEventOptions() {
            EventOption opt = new EventOption(); opt.setId(1L); opt.setName("Catering");
            when(eventOptionRepository.findAll()).thenReturn(List.of(opt));

            List<NamedItemResponse> options = eventService.getAllEventOptions();

            assertThat(options).hasSize(1);
            assertThat(options.get(0).getName()).isEqualTo("Catering");
        }
    }
}
