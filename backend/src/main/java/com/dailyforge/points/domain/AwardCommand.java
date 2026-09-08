package com.dailyforge.points.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What a caller asks the engine to do (spec §5.2). Every field is required except the
 * source pair, because not every award traces back to a row in another module (a
 * reconciliation-derived award still needs one for the reconciler to find it again
 * later, but a one-off adjustment may not).
 *
 * The engine computes nothing from this — {@code amount} already came from
 * {@code points_rule_config} by the time it reaches here. This is the boundary where
 * "the frontend never calculates points" (non-negotiable #4) becomes "no module upstream
 * of the ledger invents a number either": every field here is a plain value, not a
 * formula.
 */
public record AwardCommand(
        UUID userId,
        LocalDate occurredOn,
        PointsCategory category,
        String ruleCode,
        int amount,
        String sourceType,
        UUID sourceId,
        String description,
        String idempotencyKey) {

    public AwardCommand {
        java.util.Objects.requireNonNull(userId, "userId");
        java.util.Objects.requireNonNull(occurredOn, "occurredOn");
        java.util.Objects.requireNonNull(category, "category");
        java.util.Objects.requireNonNull(ruleCode, "ruleCode");
        java.util.Objects.requireNonNull(description, "description");
        java.util.Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }

    /** For a reversal or an award with no upstream row to point back to. */
    public static AwardCommand withoutSource(
            UUID userId,
            LocalDate occurredOn,
            PointsCategory category,
            String ruleCode,
            int amount,
            String description,
            String idempotencyKey) {
        return new AwardCommand(
                userId, occurredOn, category, ruleCode, amount, null, null, description, idempotencyKey);
    }
}
