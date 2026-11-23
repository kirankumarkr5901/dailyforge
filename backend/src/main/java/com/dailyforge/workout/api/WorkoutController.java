package com.dailyforge.workout.api;

import com.dailyforge.common.error.StaleWrite;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.workout.api.WorkoutDtos.DeleteSetResponse;
import com.dailyforge.workout.api.WorkoutDtos.ExerciseBoardEntry;
import com.dailyforge.workout.api.WorkoutDtos.LogSetRequest;
import com.dailyforge.workout.api.WorkoutDtos.PrResponse;
import com.dailyforge.workout.api.WorkoutDtos.SessionResponse;
import com.dailyforge.workout.api.WorkoutDtos.SetResponse;
import com.dailyforge.workout.api.WorkoutDtos.SetWriteResponse;
import com.dailyforge.workout.api.WorkoutDtos.UpdateSetRequest;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.workout.domain.WorkoutBoardService;
import com.dailyforge.workout.domain.WorkoutCompletionService;
import com.dailyforge.workout.domain.WorkoutSession;
import com.dailyforge.workout.domain.WorkoutSetService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sessions and sets — the tracker itself (spec §8.3). */
@RestController
@RequestMapping("/api/v1/workouts")
public class WorkoutController {

    private final WorkoutSetService setService;
    private final WorkoutBoardService board;
    private final CurrentUser currentUser;
    private final DayService dayService;
    private final IdentityService identity;
    private final WorkoutCompletionService completionService;
    private final com.dailyforge.workout.domain.WeeklyVolumeService weeklyVolume;
    private final com.dailyforge.workout.domain.DeloadService deload;

    public WorkoutController(
            WorkoutSetService setService,
            WorkoutBoardService board,
            CurrentUser currentUser,
            DayService dayService,
            IdentityService identity,
            WorkoutCompletionService completionService,
            com.dailyforge.workout.domain.WeeklyVolumeService weeklyVolume,
            com.dailyforge.workout.domain.DeloadService deload) {
        this.setService = setService;
        this.board = board;
        this.currentUser = currentUser;
        this.dayService = dayService;
        this.identity = identity;
        this.completionService = completionService;
        this.weeklyVolume = weeklyVolume;
        this.deload = deload;
    }

    /**
     * Marks the week containing {@code date} as a deload, or takes it back. Not a
     * structural change: it is a choice about this week's training, so it works with
     * "Enable edit" off.
     */
    @PutMapping("/deload")
    public WorkoutDtos.DeloadResponse setDeload(@Valid @RequestBody WorkoutDtos.DeloadRequest request) {
        UUID userId = currentUser.require();
        deload.set(userId, request.date(), request.deload());
        return new WorkoutDtos.DeloadResponse(
                com.dailyforge.workout.domain.WeeklyVolumeService.mondayOf(request.date()), request.deload());
    }

    /** Sets per muscle for the Monday-to-Sunday week containing {@code date} (today if omitted). */
    @GetMapping("/weekly-volume")
    public WorkoutDtos.WeeklyVolumeResponse weeklyVolume(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return WorkoutDtos.WeeklyVolumeResponse.of(weeklyVolume.forWeekOf(userId, resolved));
    }

    /**
     * {@code date} is optional: omitting it asks for "today" as resolved server-side
     * (spec §4.2 — only the server knows the user's local date), the same contract the
     * habit board's {@code GET /habits/board} uses for the same reason.
     */
    @GetMapping("/session")
    public SessionResponse getSession(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) Integer dayIndex) {
        UUID userId = currentUser.require();
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        WorkoutSession session = setService.getOrCreateSession(userId, resolved, planId, dayIndex);
        List<ExerciseBoardEntry> exercises =
                board.board(userId, session).stream()
                        .map(
                                be ->
                                        new ExerciseBoardEntry(
                                                be.exercise().getId(),
                                                be.exercise().getName(),
                                                be.exercise().getKind(),
                                                be.exercise().getEquipment(),
                                                be.exercise().getMuscleGroups(),
                                                be.exercise().isElite(),
                                                PrResponse.of(be.recentPr()),
                                                PrResponse.of(be.lifetimePr()),
                                                be.sets().stream().map(SetResponse::of).toList(),
                                                be.exercise().isOwnedBy(userId),
                                                be.exercise().getVersion(),
                                                be.completedAt(),
                                                be.targetSets(),
                                                be.targetReps(),
                                                be.notes(),
                                                WorkoutDtos.ProgressionResponse.of(be.progression()),
                                                be.planExerciseId(),
                                                be.restSeconds()))
                        .toList();
        return new SessionResponse(
                session.getId(),
                session.getOccurredOn(),
                session.getPlanId(),
                session.getDayIndex(),
                session.isCompleted(),
                exercises,
                deload.isDeload(userId, session.getOccurredOn()));
    }

    /**
     * "I am finished with this exercise." Carries no points of its own: the work was
     * already paid for when the sets were logged. The response still carries a points
     * envelope, because the last exercise marked complete finishes the session, and that
     * is a celebration.
     */
    @PostMapping("/sessions/{sessionId}/exercises/{exerciseId}/complete")
    public WorkoutDtos.CompletionResponse completeExercise(@PathVariable UUID sessionId, @PathVariable UUID exerciseId) {
        return WorkoutDtos.CompletionResponse.of(completionService.markComplete(sessionId, exerciseId, currentUser.require()));
    }

    @DeleteMapping("/sessions/{sessionId}/exercises/{exerciseId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenExercise(@PathVariable UUID sessionId, @PathVariable UUID exerciseId) {
        completionService.clearComplete(sessionId, exerciseId, currentUser.require());
    }

    @PostMapping("/sets")
    public SetWriteResponse logSet(@Valid @RequestBody LogSetRequest request) {
        UUID userId = currentUser.require();
        var write =
                setService.logSet(
                        userId,
                        request.date(),
                        request.exerciseId(),
                        request.planId(),
                        request.dayIndex(),
                        request.enteredWeight(),
                        request.weightMode(),
                        request.addedWeight(),
                        request.reps());
        return SetWriteResponse.of(write.set(), write.points());
    }

    @PatchMapping("/sets/{id}")
    public SetWriteResponse updateSet(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) Long ifMatch,
            @Valid @RequestBody UpdateSetRequest request) {
        UUID userId = currentUser.require();
        StaleWrite.check(ifMatch, setService.requireOwned(id, userId).getVersion(), "That set");
        var write =
                setService.updateSet(userId, id, request.enteredWeight(), request.weightMode(), request.addedWeight(), request.reps());
        return SetWriteResponse.of(write.set(), write.points());
    }

    @DeleteMapping("/sets/{id}")
    public DeleteSetResponse deleteSet(@PathVariable UUID id) {
        UUID userId = currentUser.require();
        PointsResult result = setService.deleteSet(userId, id);
        return DeleteSetResponse.of(result);
    }
}
