package com.dailyforge.assistant;

import com.dailyforge.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Ask DailyForge": answers about the user's data, and actions they confirm. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistant;
    private final CurrentUser currentUser;

    public AssistantController(AssistantService assistant, CurrentUser currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    public record AskRequest(@NotNull @Size(max = 40) List<AssistantService.Message> messages) {}

    @PostMapping("/ask")
    public AssistantService.Reply ask(@Valid @RequestBody AskRequest request) {
        return assistant.ask(currentUser.require(), request.messages());
    }

    /** Does one action the user confirmed; for today. */
    @PostMapping("/act")
    public Map<String, String> act(@RequestBody AssistantService.Action action) {
        return Map.of("message", assistant.act(currentUser.require(), action));
    }
}
