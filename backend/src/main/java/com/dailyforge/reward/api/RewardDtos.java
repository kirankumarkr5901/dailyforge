package com.dailyforge.reward.api;

import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.reward.domain.Reward;
import com.dailyforge.reward.domain.RewardRedemption;
import com.dailyforge.reward.domain.RewardService.RewardView;
import com.dailyforge.reward.domain.RewardTier;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RewardDtos {

    private RewardDtos() {}

    /**
     * {@code isRepeatable} is boxed, not a primitive {@code boolean}: a missing JSON
     * field cannot bind to a primitive on a record's canonical constructor (see
     * CreateExerciseRequest's own note on isElite, which hit exactly this) — Jackson
     * throws a body-parse error for the whole request rather than a validation error
     * naming the field. Defaulted to repeatable in the controller if omitted.
     */
    public record CreateRewardRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(1) Integer cost,
            @NotBlank @Size(max = 40) String icon,
            @NotNull RewardTier tier,
            Boolean isRepeatable,
            Integer stock) {}

    public record RewardResponse(
            UUID id,
            String name,
            int cost,
            String icon,
            RewardTier tier,
            boolean isRepeatable,
            /** The allowance per period, or null for no limit. The tier says how often it refreshes. */
            Integer stock,
            /** How much of that allowance is left right now; null when there is no limit. */
            Integer remaining,
            /** The day the allowance comes back. Null when there is no limit to come back. */
            LocalDate refreshesOn,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        /** Without allowance figures — for the write endpoints, which return the row they wrote. */
        public static RewardResponse of(Reward reward) {
            return new RewardResponse(
                    reward.getId(),
                    reward.getName(),
                    reward.getCost(),
                    reward.getIcon(),
                    reward.getTier(),
                    reward.isRepeatable(),
                    reward.getStock(),
                    null,
                    null,
                    reward.getVersion());
        }

        public static RewardResponse of(RewardView view) {
            Reward reward = view.reward();
            return new RewardResponse(
                    reward.getId(),
                    reward.getName(),
                    reward.getCost(),
                    reward.getIcon(),
                    reward.getTier(),
                    reward.isRepeatable(),
                    reward.getStock(),
                    view.remaining(),
                    reward.hasStockLimit() ? view.refreshesOn() : null,
                    reward.getVersion());
        }
    }

    public record RedeemResponse(UUID redemptionId, PointsEnvelope points) {
        public static RedeemResponse of(RewardRedemption redemption, PointsResult result) {
            return new RedeemResponse(redemption.getId(), PointsEnvelope.of(result));
        }
    }

    public record RefundResponse(PointsEnvelope points) {
        public static RefundResponse of(PointsResult result) {
            return new RefundResponse(PointsEnvelope.of(result));
        }
    }

    public record RedemptionResponse(UUID id, UUID rewardId, int pointsSpent, Instant redeemedAt, boolean refunded) {
        public static RedemptionResponse of(RewardRedemption redemption) {
            return new RedemptionResponse(
                    redemption.getId(), redemption.getRewardId(), redemption.getPointsSpent(), redemption.getRedeemedAt(), redemption.isRefunded());
        }
    }
}
