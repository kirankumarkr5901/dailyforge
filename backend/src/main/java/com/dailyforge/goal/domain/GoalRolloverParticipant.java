package com.dailyforge.goal.domain;

import com.dailyforge.goal.repo.GoalRepository;
import com.dailyforge.points.domain.RolloverParticipant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Expired incomplete goals move to FAILED at rollover with no penalty" (spec §8.6).
 * Idempotent by construction: once a goal is FAILED it no longer matches the
 * {@code status = ACTIVE} filter, so re-running this for the same date is a no-op.
 */
@Component
public class GoalRolloverParticipant implements RolloverParticipant {

    private final GoalRepository goals;

    public GoalRolloverParticipant(GoalRepository goals) {
        this.goals = goals;
    }

    @Override
    @Transactional
    public void onDayClosed(UUID userId, LocalDate closedDate) {
        for (Goal goal : goals.findAllByUserIdAndStatusAndEndDateLessThanEqual(userId, GoalStatus.ACTIVE, closedDate)) {
            goal.fail();
            goals.save(goal);
        }
    }
}
