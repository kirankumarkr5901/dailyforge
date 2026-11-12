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

    public ForgeController(ForgeService forge, ForgeSettingsProvider settings, CurrentUser currentUser) {
        this.forge = forge;
        this.settings = settings;
        this.currentUser = currentUser;
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
