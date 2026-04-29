package event_planer.project.controller;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import event_planer.project.dto.event.EventResponse;
import event_planer.project.service.EventService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * Serves a human-readable invite landing page at  GET /invite/{token}
 *
 * This is NOT a REST endpoint — it returns an HTML page rendered by Thymeleaf.
 * When a user receives an invite link (e.g. via WhatsApp or e-mail), clicking it
 * opens this page in the browser.  The page shows event details and a "Join" CTA
 * that deep-links into the Android app (or falls back to a sign-up prompt).
 */
@Controller
@RequiredArgsConstructor
@Hidden
public class InvitePageController {

    private final EventService eventService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy · HH:mm", Locale.ENGLISH);

    @GetMapping({"/invite/{token}", "/e/{token}"})
    public String showInvitePage(@PathVariable String token, Model model) {
        try {
            EventResponse event = eventService.previewByToken(token);

            model.addAttribute("event", event);
            model.addAttribute("token", token);

            // Pre-format dates for the template
            if (event.getEventDate() != null) {
                model.addAttribute("formattedDate", event.getEventDate().format(DATE_FMT));
            }
            if (event.getEventEndDate() != null) {
                model.addAttribute("formattedEndDate", event.getEventEndDate().format(DATE_FMT));
            }

            // Deep link URI for the Android app
            model.addAttribute("deepLink",
                    "eventplanner://join/" + token);

            return "invite";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "This invite link is invalid or has expired.");
            return "invite-error";
        }
    }
}
