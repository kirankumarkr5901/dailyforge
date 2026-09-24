package com.dailyforge.habit.api;

import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitBoard;
import com.dailyforge.habit.domain.HabitBoardEntry;
import com.dailyforge.habit.domain.HabitDayState;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.PointsResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HabitDtos {

    private HabitDtos() {}

    public record CreateHabitRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 40) String icon,
            @NotNull Integer points,
            @NotNull HabitType type,
            Integer penaltyPoints,
            Integer baseBonus,
            BigDecimal bonusMultiplier,
            Integer scheduleDays) {}

    public record UpdateHabitRequest(
            @Size(max = 80) String name,
            @Size(max = 40) String icon,
            Integer points,
            HabitType type,
            Integer penaltyPoints,
            Integer scheduleDays) {}

    public record ReorderRequest(@NotNull List<UUID> orderedIds) {}

    public record LogRequest(@NotNull LocalDate date) {}

    public record HabitResponse(
            UUID id,
            String name,
            String icon,
            int points,
            HabitType type,
            int penaltyPoints,
            int baseBonus,
            BigDecimal bonusMultiplier,
            int scheduleDays,
            int sortOrder,
            LocalDate activeFrom,
            boolean archived,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static HabitResponse of(Habit habit) {
            return new HabitResponse(
                    habit.getId(),
                    habit.getName(),
                    habit.getIcon(),
                    habit.getPoints(),
                    habit.getType(),
                    habit.getPenaltyPoints(),
                    habit.getBaseBonus(),
                    habit.getBonusMultiplier(),
                    habit.getScheduleDays(),
                    habit.getSortOrder(),
                    habit.getActiveFrom(),
                    habit.isArchived(),
                    habit.getVersion());
        }
    }

    /** The consistency-bonus preview spec §8.2 asks for at creation time, before any habit exists to derive it from. */
    public record BonusPreviewResponse(List<Integer> firstFourBonuses) {}

    public record BoardEntryResponse(
            UUID id,
            String name,
            String icon,
            int points,
            HabitType type,
            HabitDayState state,
            boolean plannedDone,
            int currentStreak,
            int bestStreak,
            boolean editable) {

        public static BoardEntryResponse of(HabitBoardEntry entry) {
            return new BoardEntryResponse(
                    entry.id(),
                    entry.name(),
                    entry.icon(),
                    entry.points(),
                    entry.type(),
                    entry.state(),
                    entry.plannedDone(),
                    entry.currentStreak(),
                    entry.bestStreak(),
                    entry.editable());
        }
    }

    public record BoardResponse(LocalDate date, List<BoardEntryResponse> habits, String bonusHint) {
        public static BoardResponse of(HabitBoard board) {
            return new BoardResponse(
                    board.date(), board.habits().stream().map(BoardEntryResponse::of).toList(), board.bonusHint());
        }
    }

    /** A habit write returns the habit plus, when it was a log action, the points envelope. */
    public record LogResponse(PointsEnvelope points) {
        public static LogResponse of(PointsResult result) {
            return new LogResponse(PointsEnvelope.of(result));
        }
    }
}
