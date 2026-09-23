package com.dailyforge.goal.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.goal.api.GoalDtos.CreateGoalRequest;
import com.dailyforge.goal.api.GoalDtos.ExtendGoalRequest;
import com.dailyforge.goal.api.GoalDtos.GoalResponse;
import com.dailyforge.goal.domain.GoalService;
import com.dailyforge.goal.domain.GoalStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Goals (spec §8.6). */
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;
    private final CurrentUser currentUser;

    public GoalController(GoalService goalService, CurrentUser currentUser) {
        this.goalService = goalService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<GoalResponse> list(@RequestParam(required = false) GoalStatus status) {
        return goalService.list(currentUser.require(), status).stream().map(GoalResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@Valid @RequestBody CreateGoalRequest request) {
        var goal =
                goalService.create(
                        currentUser.require(),
                        request.title().trim(),
                        request.description(),
                        request.kind(),
                        request.periodType(),
                        request.startDate(),
                        request.targetDate(),
                        request.rewardPoints(),
                        request.habitId(),
                        request.exerciseId(),
                        request.targetValue());
        return GoalResponse.of(goal);
    }

    @PostMapping("/{id}/complete")
    public GoalResponse complete(@PathVariable UUID id) {
        return GoalResponse.of(goalService.complete(id, currentUser.require()));
    }

    @PostMapping("/{id}/reopen")
    public GoalResponse reopen(@PathVariable UUID id) {
        return GoalResponse.of(goalService.reopen(id, currentUser.require()));
    }

    @PostMapping("/{id}/extend")
    public GoalResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendGoalRequest request) {
        return GoalResponse.of(goalService.extend(id, currentUser.require(), request.newEndDate()));
    }

    @PatchMapping("/{id}/archive")
    public GoalResponse archive(@PathVariable UUID id) {
        return GoalResponse.of(goalService.archive(id, currentUser.require()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        goalService.delete(id, currentUser.require());
    }
}
