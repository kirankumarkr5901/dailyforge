package com.dailyforge.goal.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.goal.domain.GoalPlannerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The AI goal planner: draft, accept, milestones and the weekly re-tune. */
@RestController
@RequestMapping("/api/v1/goals/ai")
public class GoalPlannerController {

    private final GoalPlannerService planner;
    private final CurrentUser currentUser;

    public GoalPlannerController(GoalPlannerService planner, CurrentUser currentUser) {
        this.planner = planner;
        this.currentUser = currentUser;
    }

    public record DraftRequest(@NotBlank @Size(max = 500) String request, @NotNull LocalDate targetDate) {}

    /** A plan to look over and edit; nothing is created. */
    @PostMapping("/draft")
    public GoalPlannerService.Draft draft(@Valid @RequestBody DraftRequest request) {
        return planner.draft(currentUser.require(), request.request(), request.targetDate());
    }

    /**
     * Creates what the user kept: the goal, milestones, habits, skill and workout
     * additions. Not a structural change (owner request): changes the AI makes, after
     * the user reviewed them, work with "Enable edit" off; the lock is for manual edits.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> accept(@RequestBody GoalPlannerService.Draft draft) {
        return Map.of("goalId", planner.accept(currentUser.require(), draft));
    }

    /** Milestones and this week's review for every AI-planned goal. */
    @GetMapping("/plans")
    public List<GoalPlannerService.GoalPlan> plans() {
        return planner.plans(currentUser.require());
    }

    @PostMapping("/milestones/{id}/toggle")
    public List<GoalPlannerService.GoalPlan> toggle(@PathVariable UUID id) {
        return planner.toggleMilestone(currentUser.require(), id);
    }

    @PostMapping("/{goalId}/review/accept")
    public List<GoalPlannerService.GoalPlan> acceptReview(@PathVariable UUID goalId) {
        return planner.answerReview(currentUser.require(), goalId, true);
    }

    @PostMapping("/{goalId}/review/dismiss")
    public List<GoalPlannerService.GoalPlan> dismissReview(@PathVariable UUID goalId) {
        return planner.answerReview(currentUser.require(), goalId, false);
    }
}
