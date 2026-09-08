package com.dailyforge.points.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The rule-code-to-celebration mapping is the one place that decides which animation
 * plays (spec §5.2, §9.4) — the frontend must never infer this itself, which makes a
 * wrong or missing mapping here a silently broken celebration, not a loud one.
 */
class CelebrationTest {

    @Test
    void everyRuleCodeNamedInTheSpecMapsToItsCelebration() {
        assertThat(Celebration.typeFor("WORKOUT_PR")).contains(CelebrationType.PR);
        assertThat(Celebration.typeFor("HABIT_CONSISTENCY")).contains(CelebrationType.STREAK);
        assertThat(Celebration.typeFor("RUN_MILESTONE")).contains(CelebrationType.MILESTONE);
        assertThat(Celebration.typeFor("RUN_FIRST_MILESTONE")).contains(CelebrationType.MILESTONE);
        assertThat(Celebration.typeFor("HABIT_COMMITMENT")).contains(CelebrationType.ALL_HABITS_DONE);
        assertThat(Celebration.typeFor("WORKOUT_SESSION_COMPLETE")).contains(CelebrationType.WORKOUT_COMPLETE);
    }

    @Test
    void aRuleCodeWithNoCelebrationMapsToEmptyRatherThanGuessing() {
        assertThat(Celebration.typeFor("HABIT_BASE")).isEqualTo(Optional.empty());
        assertThat(Celebration.typeFor("RUN_DISTANCE")).isEqualTo(Optional.empty());
        assertThat(Celebration.typeFor("HABIT_PENALTY")).isEqualTo(Optional.empty());
    }

    @Test
    void detailsCarryWhateverTheAnimationNeeds() {
        Celebration streak = Celebration.of(CelebrationType.STREAK, Map.of("streakLength", 14));

        assertThat(streak.details()).containsEntry("streakLength", 14);
    }

    @Test
    void aNullDetailsMapBecomesEmptyRatherThanNull() {
        Celebration celebration = new Celebration(CelebrationType.PR, null);

        assertThat(celebration.details()).isEmpty();
    }

    @Test
    void detailsAreDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("a", 1);
        Celebration celebration = Celebration.of(CelebrationType.PR, mutable);

        mutable.put("b", 2);

        assertThat(celebration.details()).containsOnlyKeys("a");
    }
}
