package event_planer.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Hidden;

@Controller
@Hidden
public class LegalPageController {

    @GetMapping("/privacy")
    public String privacyPolicy() {
        return "privacy";
    }

    @GetMapping("/account-deletion")
    public String accountDeletion() {
        return "account-deletion";
    }
}
