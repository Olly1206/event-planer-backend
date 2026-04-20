package event_planer.project.controller;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import event_planer.project.dto.NamedItemResponse;
import event_planer.project.dto.event.CreateEventRequest;
import event_planer.project.dto.event.EventResponse;
import event_planer.project.dto.event.UpdateEventRequest;
import event_planer.project.service.EventService;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock private EventService eventService;
    @InjectMocks private EventController eventController;

    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUpAuth() {
        var auth = new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private EventResponse stubEvent(Long id) {
        EventResponse event = new EventResponse();
        event.setId(id);
        event.setTitle("Test Event");
        return event;
    }

    @Nested
    class Create {

        @Test
        void returns201WithBody() {
            CreateEventRequest request = new CreateEventRequest();
            when(eventService.createEvent(eq(request), eq(USER_ID))).thenReturn(stubEvent(1L));

            ResponseEntity<EventResponse> response = eventController.createEvent(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().getId()).isEqualTo(1L);
        }
    }

    @Nested
    class Read {

        @Test
        void getAllReturns200() {
            when(eventService.getAllEvents()).thenReturn(List.of(stubEvent(1L), stubEvent(2L)));

            ResponseEntity<List<EventResponse>> response = eventController.getAllEvents();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        void getByIdDelegatesToService() {
            when(eventService.getEventById(eq(5L), eq(USER_ID))).thenReturn(stubEvent(5L));

            ResponseEntity<EventResponse> response = eventController.getEventById(5L);

            assertThat(response.getBody().getId()).isEqualTo(5L);
        }

        @Test
        void getByOrganiserDelegatesToService() {
            when(eventService.getEventsByOrganiser(eq(3L), eq(USER_ID)))
                    .thenReturn(List.of(stubEvent(1L)));

            ResponseEntity<List<EventResponse>> response = eventController.getEventsByOrganiser(3L);

            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void searchDelegatesToService() {
            when(eventService.searchEvents("conference")).thenReturn(List.of(stubEvent(1L)));

            ResponseEntity<List<EventResponse>> response = eventController.searchEvents("conference");

            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getJoinedEventsUsesCurrentUser() {
            when(eventService.getJoinedEvents(USER_ID)).thenReturn(List.of(stubEvent(1L)));

            ResponseEntity<List<EventResponse>> response = eventController.getJoinedEvents();

            verify(eventService).getJoinedEvents(USER_ID);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getMyEventsUsesCurrentUser() {
            when(eventService.getCreatedEvents(USER_ID)).thenReturn(List.of());

            ResponseEntity<List<EventResponse>> response = eventController.getMyEvents();

            verify(eventService).getCreatedEvents(USER_ID);
        }
    }

    @Nested
    class Update {

        @Test
        void returns200WithUpdatedEvent() {
            UpdateEventRequest request = new UpdateEventRequest();
            when(eventService.updateEvent(eq(1L), eq(request), eq(USER_ID)))
                    .thenReturn(stubEvent(1L));

            ResponseEntity<EventResponse> response = eventController.updateEvent(1L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    class Delete {

        @Test
        void returns204NoContent() {
            ResponseEntity<Void> response = eventController.deleteEvent(1L);

            verify(eventService).deleteEvent(1L, USER_ID);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    class Participants {

        @Test
        void joinReturns200() {
            ResponseEntity<Void> response = eventController.joinEvent(1L, null);

            verify(eventService).joinEventWithName(1L, USER_ID, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void leaveReturns204() {
            ResponseEntity<Void> response = eventController.leaveEvent(1L);

            verify(eventService).leaveEvent(1L, USER_ID);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    class InviteLink {

        @Test
        void previewByTokenReturns200() {
            when(eventService.previewByToken("abc")).thenReturn(stubEvent(1L));

            ResponseEntity<EventResponse> response = eventController.previewByToken("abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void joinByTokenDelegatesToService() {
            eventController.joinByToken("abc", null);

            verify(eventService).joinByTokenWithName("abc", USER_ID, null);
        }

        @Test
        void getInviteLinkReturnsToken() {
            when(eventService.getInviteToken(1L, USER_ID)).thenReturn("secret-token");

            ResponseEntity<String> response = eventController.getInviteLink(1L);

            assertThat(response.getBody()).isEqualTo("secret-token");
        }
    }

    @Nested
    class AdminManagement {

        @Test
        void addAdminDelegatesToService() {
            eventController.addAdmin(1L, "john");

            verify(eventService).addAdmin(1L, "john", USER_ID);
        }

        @Test
        void removeAdminDelegatesToService() {
            eventController.removeAdmin(1L, 99L);

            verify(eventService).removeAdmin(1L, 99L, USER_ID);
        }
    }

    @Nested
    class ReferenceData {

        @Test
        void getEventTypesReturns200() {
            when(eventService.getAllEventTypes()).thenReturn(List.of(new NamedItemResponse(1L, "Conference")));

            ResponseEntity<List<NamedItemResponse>> response = eventController.getEventTypes();

            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void getEventOptionsReturns200() {
            when(eventService.getAllEventOptions()).thenReturn(List.of(new NamedItemResponse(1L, "Catering")));

            ResponseEntity<List<NamedItemResponse>> response = eventController.getEventOptions();

            assertThat(response.getBody()).hasSize(1);
        }
    }
}
