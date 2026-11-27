package com.dailyforge.ai;

import com.dailyforge.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Whether the AI features are set up on this server, so screens can hide them when not. */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final GeminiClient gemini;
    private final CurrentUser currentUser;

    public AiController(GeminiClient gemini, CurrentUser currentUser) {
        this.gemini = gemini;
        this.currentUser = currentUser;
    }

    public record Status(boolean enabled) {}

    @GetMapping("/status")
    public Status status() {
        currentUser.require();
        return new Status(gemini.enabled());
    }
}
