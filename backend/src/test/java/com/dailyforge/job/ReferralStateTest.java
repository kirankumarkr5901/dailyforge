package com.dailyforge.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.job.domain.ReferralProperties;
import com.dailyforge.job.domain.ReferralState;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Where each day of waiting lands.
 *
 * Pinned because the rule arrived with ambiguous boundaries — "under 2 days" green,
 * "under 3 and over 2" orange, "over 3" red describes two gaps and an overlap, and
 * days two and three belong to nobody under a literal reading. Read as whole days
 * waited the intent is unmistakable, and this is the table that says so: every day
 * lands in exactly one state, and the day a state changes is written down rather than
 * left to be rediscovered from the arithmetic.
 */
class ReferralStateTest {

    private final ReferralProperties properties = new ReferralProperties(2, 4);
    private static final LocalDate ASKED = LocalDate.of(2026, 3, 10);

    private ReferralState after(int days) {
        return properties.stateOn(ASKED, ASKED.plusDays(days));
    }

    @Test
    void theFirstTwoDaysAreWorthWaitingThrough() {
        assertThat(after(0)).isEqualTo(ReferralState.WAITING);
        assertThat(after(1)).isEqualTo(ReferralState.WAITING);
    }

    @Test
    void daysTwoAndThreeAreWorthAPoliteNudge() {
        assertThat(after(2)).isEqualTo(ReferralState.FOLLOW_UP);
        assertThat(after(3)).isEqualTo(ReferralState.FOLLOW_UP);
    }

    @Test
    void fromTheFourthDayTheReferralShouldStopBlockingYou() {
        assertThat(after(4)).isEqualTo(ReferralState.APPLY_DIRECTLY);
        assertThat(after(30)).isEqualTo(ReferralState.APPLY_DIRECTLY);
    }

    /**
     * A referral nobody recorded an ask date for cannot have a clock run on it. Guessing
     * one would be worse than admitting there is none: inventing "today" would hide it
     * at the bottom of the queue forever, and inventing "long ago" would shout at the
     * user about a referral that might have gone out this morning.
     */
    @Test
    void aReferralWithNoRecordedAskDateIsSimplyWaiting() {
        assertThat(properties.stateOn(null, ASKED)).isEqualTo(ReferralState.WAITING);
        assertThat(properties.daysWaiting(null, ASKED)).isZero();
    }

    /** Clock skew or a typo should not produce a negative age. */
    @Test
    void aFutureAskDateReadsAsNoTimeWaited() {
        assertThat(properties.daysWaiting(ASKED, ASKED.minusDays(3))).isZero();
        assertThat(after(-3)).isEqualTo(ReferralState.WAITING);
    }

    /** The thresholds are configuration, so a user who is more or less patient can move them. */
    @Test
    void thresholdsComeFromConfigurationRatherThanBeingBakedIn() {
        ReferralProperties patient = new ReferralProperties(5, 10);
        assertThat(patient.stateOn(ASKED, ASKED.plusDays(4))).isEqualTo(ReferralState.WAITING);
        assertThat(patient.stateOn(ASKED, ASKED.plusDays(5))).isEqualTo(ReferralState.FOLLOW_UP);
        assertThat(patient.stateOn(ASKED, ASKED.plusDays(10))).isEqualTo(ReferralState.APPLY_DIRECTLY);
    }

    /** Zero or negative configuration falls back to the documented defaults, not to chaos. */
    @Test
    void unsetConfigurationFallsBackToTheDefaults() {
        ReferralProperties defaults = new ReferralProperties(0, 0);
        assertThat(defaults.followUpAfterDays()).isEqualTo(2);
        assertThat(defaults.applyAfterDays()).isEqualTo(4);
    }
}
