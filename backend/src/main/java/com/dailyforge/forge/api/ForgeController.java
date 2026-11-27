package com.dailyforge.forge.api;

import com.dailyforge.common.security.StructuralChange;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.forge.api.ForgeDtos.OverviewResponse;
import com.dailyforge.forge.api.ForgeDtos.QuestChangeResponse;
import com.dailyforge.forge.api.ForgeDtos.QuestionnaireResponse;
import com.dailyforge.forge.api.ForgeDtos.StartRequest;
import com.dailyforge.forge.domain.ForgeService;
import com.dailyforge.forge.domain.ForgeSettingsProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forge 66. Marking and unmarking are both idempotent by state — asking for the state a
 * quest is already in changes nothing and pays nothing — so a retried request is safe
 * without a separate key.
 */
@RestController
@RequestMapping("/api/v1/forge")
public class ForgeController {

    private final ForgeService forge;
    private final ForgeSettingsProvider settings;
    private final CurrentUser currentUser;

    private final com.dailyforge.forge.domain.ForgeAiPlanner ai;

    public ForgeController(
            ForgeService forge,
            ForgeSettingsProvider settings,
            CurrentUser currentUser,
            com.dailyforge.forge.domain.ForgeAiPlanner ai) {
        this.forge = forge;
        this.settings = settings;
        this.currentUser = currentUser;
        this.ai = ai;
    }

    public record AiTurn(
            @jakarta.validation.constraints.Size(max = 300) String question,
            @jakarta.validation.constraints.Size(max = 500) String answer) {}

    public record AiInterviewRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 30) java.util.List<@Valid AiTurn> turns) {}

    public record AiStartRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 30) java.util.List<@Valid AiTurn> turns,
            @jakarta.validation.constraints.NotNull com.dailyforge.forge.domain.ForgeMode mode) {}

    private static java.util.List<com.dailyforge.forge.domain.ForgeAiPlanner.Turn> turns(java.util.List<AiTurn> turns) {
        return turns.stream().map(t -> new com.dailyforge.forge.domain.ForgeAiPlanner.Turn(t.question(), t.answer())).toList();
    }

    /** The AI interview: the answers so far in, the next question (or done) out. Nothing is stored. */
    @PostMapping("/ai/interview")
    public com.dailyforge.forge.domain.ForgeAiPlanner.NextQuestion interview(@Valid @RequestBody AiInterviewRequest request) {
        return ai.nextQuestion(currentUser.require(), turns(request.turns()));
    }

    /** Starts a program the AI plans from the interview, a week at a time. */
    @PostMapping("/ai/program")
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public OverviewResponse startAi(@Valid @RequestBody AiStartRequest request) {
        return OverviewResponse.of(forge.startAi(currentUser.require(), turns(request.turns()), request.mode()));
    }

    @GetMapping("/questionnaire")
    public QuestionnaireResponse questionnaire() {
        currentUser.require();
        return QuestionnaireResponse.of(settings.current().lengthDays());
    }

    @GetMapping
    public OverviewResponse overview() {
        return OverviewResponse.of(forge.overview(currentUser.require()));
    }

    @PostMapping("/program")
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public OverviewResponse start(@Valid @RequestBody StartRequest request) {
        return OverviewResponse.of(forge.start(currentUser.require(), request.answers(), request.mode()));
    }

    @DeleteMapping("/program")
    @StructuralChange
    public OverviewResponse abandon() {
        return OverviewResponse.of(forge.abandon(currentUser.require()));
    }

    @PostMapping("/quests/{id}/completion")
    public QuestChangeResponse complete(@PathVariable UUID id) {
        return QuestChangeResponse.of(forge.markDone(currentUser.require(), id));
    }

    @DeleteMapping("/quests/{id}/completion")
    public QuestChangeResponse uncomplete(@PathVariable UUID id) {
        return QuestChangeResponse.of(forge.markNotDone(currentUser.require(), id));
    }
}
