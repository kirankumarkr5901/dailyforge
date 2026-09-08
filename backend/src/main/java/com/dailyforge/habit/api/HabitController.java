package com.dailyforge.habit.api;

import com.dailyforge.common.error.StaleWrite;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.habit.api.HabitDtos.BonusPreviewResponse;
import com.dailyforge.habit.api.HabitDtos.BoardResponse;
import com.dailyforge.habit.api.HabitDtos.CreateHabitRequest;
import com.dailyforge.habit.api.HabitDtos.HabitResponse;
import com.dailyforge.habit.api.HabitDtos.LogRequest;
import com.dailyforge.habit.api.HabitDtos.LogResponse;
import com.dailyforge.habit.api.HabitDtos.ReorderRequest;
import com.dailyforge.habit.api.HabitDtos.UpdateHabitRequest;
import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitBoardService;
import com.dailyforge.habit.domain.HabitLogService;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitStreakCalculator;
import com.dailyforge.points.domain.PointsRuleConfigService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HabitController {

    private final HabitService habits;
    private final HabitLogService habitLogs;
    private final HabitBoardService board;
    private final PointsRuleConfigService ruleConfigs;
    private final IdentityService identity;
    private final DayService dayService;
    private final CurrentUser currentUser;

    public HabitController(
            HabitService habits,
            HabitLogService habitLogs,
            HabitBoardService board,
            PointsRuleConfigService ruleConfigs,
            IdentityService identity,
            DayService dayService,
            CurrentUser currentUser) {
        this.habits = habits;
        this.habitLogs = habitLogs;
        this.board = board;
        this.ruleConfigs = ruleConfigs;
        this.identity = identity;
        this.dayService = dayService;
        this.currentUser = currentUser;
    }

    @GetMapping("/habits")
    public List<HabitResponse> list() {
        return habits.listActive(currentUser.require()).stream().map(HabitResponse::of).toList();
    }

    @PostMapping("/habits")
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@Valid @RequestBody CreateHabitRequest request) {
        UUID userId = currentUser.require();
        validateScheduleDays(request.scheduleDays());

        // "today" for a brand-new habit's activeFrom must be the owner's own local date
        // (non-negotiable #5) — never the server's or a client-supplied one.
        var zone = dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
        LocalDate today = dayService.today(zone);

        Habit habit =
                habits.create(
                        userId,
                        request.name().trim(),
                        request.icon(),
                        requirePositive(request.points(), "points"),
                        request.type(),
                        request.penaltyPoints() != null ? request.penaltyPoints() : 0,
                        request.baseBonus(),
                        request.bonusMultiplier(),
                        request.scheduleDays() != null
                                ? request.scheduleDays()
                                : com.dailyforge.habit.domain.ScheduleDays.EVERY_DAY,
                        today);
        return HabitResponse.of(habit);
    }

    @PatchMapping("/habits/{id}")
    public HabitResponse update(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) Long ifMatch,
            @Valid @RequestBody UpdateHabitRequest request) {
        UUID userId = currentUser.require();
        StaleWrite.check(ifMatch, habits.requireOwned(id, userId).getVersion(), "That habit");
        if (request.scheduleDays() != null) {
            validateScheduleDays(request.scheduleDays());
        }
        Habit habit =
                habits.update(
                        id,
                        userId,
                        request.name(),
                        request.icon(),
                        request.points(),
                        request.type(),
                        request.penaltyPoints(),
                        request.scheduleDays());
        return HabitResponse.of(habit);
    }

    @PatchMapping("/habits/order")
    public List<HabitResponse> reorder(@Valid @RequestBody ReorderRequest request) {
        UUID userId = currentUser.require();
        habits.reorder(userId, request.orderedIds());
        return list();
    }

    @DeleteMapping("/habits/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        habits.deleteOrArchive(id, currentUser.require());
    }

    /**
     * {@code date} is optional: omitting it asks for "today" as resolved server-side
     * (spec §4.2 — only the server knows the user's local date), which is how a client
     * that has not yet computed any date opens the board for the first time. The
     * resolved date comes back in the response either way.
     */
    @GetMapping("/habits/board")
    public BoardResponse getBoard(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        LocalDate resolved = date != null ? date : dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
        return BoardResponse.of(board.board(userId, resolved));
    }

    /**
     * The live preview spec §8.2 asks for at habit creation, before the habit (and
     * therefore any real streak) exists — computed from the same formula and the same
     * defaults a real habit would use, purely to show the consequence of a multiplier
     * before the user commits to it.
     */
    @GetMapping("/habits/bonus-preview")
    public BonusPreviewResponse bonusPreview(
            @RequestParam(required = false) Integer baseBonus,
            @RequestParam(required = false) BigDecimal bonusMultiplier) {
        var config = ruleConfigs.getSystemDefault("HABIT_CONSISTENCY");
        int base = baseBonus != null ? baseBonus : config.getInt("defaultBaseBonus");
        BigDecimal multiplier =
                bonusMultiplier != null ? bonusMultiplier : BigDecimal.valueOf(config.getDouble("defaultMultiplier"));
        int maxExponent = config.getInt("maxExponent");

        List<Integer> preview =
                List.of(1, 2, 3, 4).stream()
                        .map(n -> HabitStreakCalculator.bonusAmount(base, multiplier, n, maxExponent))
                        .toList();
        return new BonusPreviewResponse(preview);
    }

    @PostMapping("/habits/{id}/logs")
    public LogResponse log(@PathVariable UUID id, @Valid @RequestBody LogRequest request) {
        var result = habitLogs.log(id, currentUser.require(), request.date());
        return LogResponse.of(result);
    }

    @DeleteMapping("/habits/{id}/logs/{date}")
    public LogResponse unlog(
            @PathVariable UUID id,
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        var result = habitLogs.unlog(id, currentUser.require(), date);
        return LogResponse.of(result);
    }

    private void validateScheduleDays(Integer scheduleDays) {
        if (scheduleDays != null && (scheduleDays < 1 || scheduleDays > 127)) {
            throw ApiException.outOfRange("scheduleDays", "That schedule is not valid.");
        }
    }

    private int requirePositive(int value, String field) {
        if (value < 0) {
            throw ApiException.outOfRange(field, "That looks out of range. Check the value.");
        }
        return value;
    }
}
