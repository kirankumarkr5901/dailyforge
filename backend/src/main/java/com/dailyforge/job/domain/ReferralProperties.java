package com.dailyforge.job.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long a referral is worth waiting on before it stops being worth waiting on.
 *
 * <p>The owner's rule was "under two days is fine, two to three days means chase it,
 * past three days apply anyway". Those numbers are a judgement about other people's
 * response times, not a fact, so they live in configuration rather than as literals at
 * the point of comparison — the same reason no point value is ever written inline.
 *
 * @param followUpAfterDays days of waiting after which a nudge is reasonable.
 * @param applyAfterDays    days of waiting after which the referral should stop
 *                          blocking a direct application.
 */
@ConfigurationProperties(prefix = "dailyforge.referral")
public record ReferralProperties(int followUpAfterDays, int applyAfterDays) {

    public ReferralProperties {
        followUpAfterDays = followUpAfterDays <= 0 ? 2 : followUpAfterDays;
        applyAfterDays = applyAfterDays <= 0 ? 4 : applyAfterDays;
    }

    /**
     * Which state a referral asked for on {@code requestedOn} is in as of {@code today}.
     *
     * <p>The boundaries are closed deliberately. The owner's phrasing left days two and
     * three ambiguous — "under 2" green, "under 3 and over 2" orange, "over 3" red
     * describes two gaps and an overlap. Read as whole days of waiting, the intent is
     * clear and every day lands in exactly one state: 0-1 wait, 2-3 chase, 4+ act.
     *
     * <p>A referral with no request date has no clock and is treated as still waiting;
     * that is the honest answer to "how long has this been out?" when nobody recorded
     * when it went out.
     */
    public ReferralState stateOn(LocalDate requestedOn, LocalDate today) {
        if (requestedOn == null) {
            return ReferralState.WAITING;
        }
        long waited = daysWaiting(requestedOn, today);
        if (waited >= applyAfterDays) {
            return ReferralState.APPLY_DIRECTLY;
        }
        return waited >= followUpAfterDays ? ReferralState.FOLLOW_UP : ReferralState.WAITING;
    }

    /** Whole days between the ask and today; never negative, so a future date reads as 0. */
    public long daysWaiting(LocalDate requestedOn, LocalDate today) {
        if (requestedOn == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(requestedOn, today));
    }
}
