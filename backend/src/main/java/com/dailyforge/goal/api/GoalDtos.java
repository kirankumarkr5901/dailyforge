package com.dailyforge.goal.api;

import com.dailyforge.goal.domain.Goal;
import com.dailyforge.goal.domain.GoalKind;
import com.dailyforge.goal.domain.GoalPeriodType;
import com.dailyforge.goal.domain.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {}

    public record CreateGoalRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 500) String description,
            @NotNull GoalKind kind,
            @NotNull GoalPeriodType periodType,
            @NotNull LocalDate startDate,
            LocalDate targetDate,
            Integer rewardPoints,
            UUID habitId,
            UUID exerciseId,
            BigDecimal targetValue) {}

    public record ExtendGoalRequest(@NotNull LocalDate newEndDate) {}

    public record GoalResponse(
            UUID id,
            String title,
            String description,
            GoalKind kind,
            GoalPeriodType periodType,
            LocalDate startDate,
            LocalDate endDate,
            int rewardPoints,
            GoalStatus status,
            UUID habitId,
            UUID exerciseId,
            BigDecimal targetValue,
            BigDecimal currentValue,
            double progressFraction) {

        public static GoalResponse of(Goal goal) {
            double fraction =
                    goal.getTargetValue() != null && goal.getTargetValue().signum() > 0
                            ? Math.min(1.0, goal.getCurrentValue().divide(goal.getTargetValue(), 4, java.math.RoundingMode.HALF_UP).doubleValue())
                            : (goal.getStatus() == GoalStatus.COMPLETED ? 1.0 : 0.0);
            return new GoalResponse(
                    goal.getId(),
                    goal.getTitle(),
                    goal.getDescription(),
                    goal.getKind(),
                    goal.getPeriodType(),
                    goal.getStartDate(),
                    goal.getEndDate(),
                    goal.getRewardPoints(),
                    goal.getStatus(),
                    goal.getHabitId(),
                    goal.getExerciseId(),
                    goal.getTargetValue(),
                    goal.getCurrentValue(),
                    fraction);
        }
    }
}
