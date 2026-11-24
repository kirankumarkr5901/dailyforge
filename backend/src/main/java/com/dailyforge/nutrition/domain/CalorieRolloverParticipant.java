package com.dailyforge.nutrition.domain;

import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import com.dailyforge.points.domain.RolloverParticipant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * When a user's day closes, judge it against the calorie budget. Idempotent, as the
 * rollover requires: reconciling the same closed day twice changes nothing.
 */
@Component
public class CalorieRolloverParticipant implements RolloverParticipant {

    private final PointsService points;

    public CalorieRolloverParticipant(PointsService points) {
        this.points = points;
    }

    @Override
    public void onDayClosed(UUID userId, LocalDate closedDate) {
        points.reconcile(userId, new ReconcileScope.CalorieDay(userId, closedDate));
    }
}
