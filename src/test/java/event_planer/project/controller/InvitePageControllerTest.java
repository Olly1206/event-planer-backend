package event_planer.project.controller;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import event_planer.project.dto.event.EventResponse;
import event_planer.project.entity.Event;
import event_planer.project.exception.ResourceNotFoundException;
import event_planer.project.service.EventService;

@ExtendWith(MockitoExtension.class)
class InvitePageControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private Model model;

    private InvitePageController invitePageController;

    @BeforeEach
    void setUp() {
        invitePageController = new InvitePageController(eventService);
    }

    @Nested
    class ShowInvitePage {

        @Test
        void returnsInviteTemplateWhenTokenValid() {
            // Arrange
            String token = "valid-token-123";
            EventResponse event = stubEventResponse(1L, "Team Lunch");

            when(eventService.previewByToken(token)).thenReturn(event);

            // Act
            String result = invitePageController.showInvitePage(token, model);

            // Assert
            assertThat(result).isEqualTo("invite");
            verify(model).addAttribute("event", event);
            verify(model).addAttribute("token", token);
        }

        @Test
        void setsDeepLinkUriInModel() {
            // Arrange
            String token = "invite-token-abc";
            EventResponse event = stubEventResponse(1L, "Conference");

            when(eventService.previewByToken(token)).thenReturn(event);

            // Act
            invitePageController.showInvitePage(token, model);

            // Assert
            ArgumentCaptor<String> deepLinkCaptor = ArgumentCaptor.forClass(String.class);
            verify(model).addAttribute(eq("deepLink"), deepLinkCaptor.capture());

            assertThat(deepLinkCaptor.getValue())
                    .isEqualTo("eventplanner://invite/invite-token-abc");
        }

        @Test
        void formatsEventDateInModel() {
            // Arrange
            String token = "token";
            LocalDateTime eventDate = LocalDateTime.of(2026, 5, 15, 14, 30);
            EventResponse event = stubEventResponse(1L, "Meeting");
            event.setEventDate(eventDate);

            when(eventService.previewByToken(token)).thenReturn(event);

            // Act
            invitePageController.showInvitePage(token, model);

            // Assert
            ArgumentCaptor<String> dateCaptor = ArgumentCaptor.forClass(String.class);
            verify(model).addAttribute(eq("formattedDate"), dateCaptor.capture());

            // Should format as "Friday, 16 May 2026 · 14:30" or similar
            assertThat(dateCaptor.getValue())
                    .contains("May")
                    .contains("2026")
                    .contains("14:30");
        }

        @Test
        void returnsErrorTemplateWhenTokenInvalid() {
            // Arrange
            String token = "invalid-token";
            when(eventService.previewByToken(token))
                    .thenThrow(new ResourceNotFoundException("Invalid invite link"));

            // Act
            String result = invitePageController.showInvitePage(token, model);

            // Assert
            assertThat(result).isEqualTo("invite-error");
            ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
            verify(model).addAttribute(eq("errorMessage"), errorCaptor.capture());

            assertThat(errorCaptor.getValue())
                    .contains("invalid")
                    .doesNotContain("null");
        }

        @Test
        void returnsErrorTemplateWhenServiceThrowsException() {
            // Arrange
            String token = "error-token";
            when(eventService.previewByToken(token))
                    .thenThrow(new RuntimeException("Database error"));

            // Act
            String result = invitePageController.showInvitePage(token, model);

            // Assert
            assertThat(result).isEqualTo("invite-error");
            verify(model).addAttribute(eq("errorMessage"), any(String.class));
        }

        @Test
        void addsEventEndDateWhenPresent() {
            // Arrange
            String token = "token-with-end-date";
            LocalDateTime startDate = LocalDateTime.of(2026, 5, 15, 9, 0);
            LocalDateTime endDate = LocalDateTime.of(2026, 5, 15, 17, 0);

            EventResponse event = stubEventResponse(1L, "Conference");
            event.setEventDate(startDate);
            event.setEventEndDate(endDate);

            when(eventService.previewByToken(token)).thenReturn(event);

            // Act
            invitePageController.showInvitePage(token, model);

            // Assert
            verify(model).addAttribute("event", event);
            ArgumentCaptor<String> endDateCaptor = ArgumentCaptor.forClass(String.class);
            verify(model).addAttribute(eq("formattedEndDate"), endDateCaptor.capture());

            assertThat(endDateCaptor.getValue()).contains("17:00");
        }

        @Test
        void skipsEndDateFormattingWhenNull() {
            // Arrange
            String token = "token-no-end-date";
            EventResponse event = stubEventResponse(1L, "Quick Meeting");
            event.setEventDate(LocalDateTime.of(2026, 5, 15, 10, 0));
            event.setEventEndDate(null);

            when(eventService.previewByToken(token)).thenReturn(event);

            // Act
            invitePageController.showInvitePage(token, model);

            // Assert - verify that event was added to model
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            
            verify(model, org.mockito.Mockito.atLeastOnce()).addAttribute(keyCaptor.capture(), valueCaptor.capture());
            
            // Verify "formattedEndDate" was not added when endDate is null
            assertThat(keyCaptor.getAllValues()).doesNotContain("formattedEndDate");
        }
    }

    // ─ Helper methods ─

    private EventResponse stubEventResponse(Long id, String title) {
        EventResponse response = new EventResponse();
        response.setId(id);
        response.setTitle(title);
        response.setDescription("Test event");
        response.setVisibility(Event.Visibility.PUBLIC);
        response.setEventDate(LocalDateTime.now().plusDays(7));
        return response;
    }
}
