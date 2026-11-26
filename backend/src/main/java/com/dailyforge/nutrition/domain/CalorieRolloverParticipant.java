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
    private final com.dailyforge.identity.domain.IdentityService identity;

    public CalorieRolloverParticipant(PointsService points, com.dailyforge.identity.domain.IdentityService identity) {
        this.points = points;
        this.identity = identity;
    }

    @Override
    public void onDayClosed(UUID userId, LocalDate closedDate) {
        // Nutrition switched off: its automatic points pause (nothing judged, nothing reversed).
        if (!identity.requireSettings(userId).isFeatureEnabled("NUTRITION")) {
            return;
        }
        points.reconcile(userId, new ReconcileScope.CalorieDay(userId, closedDate));
    }
}
