package com.dailyforge.workout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The facts the coach works out itself, and how the AI's advice is checked. No network. */
class WorkoutCoachServiceTest {

    private static WorkoutCoachService.Session s(int day, String kg, Integer... reps) {
        return new WorkoutCoachService.Session(LocalDate.of(2026, 11, day), new BigDecimal(kg), List.of(reps));
    }

    @Test
    void threeSessionsWithoutMoreWeightOrRepsIsStalled() {
        assertThat(WorkoutCoachService.stalled(List.of(s(20, "40", 8, 8), s(17, "40", 8, 7), s(14, "40", 8, 8), s(11, "40", 8, 8))))
                .isTrue();
        // One more rep at the same weight is progress.
        assertThat(WorkoutCoachService.stalled(List.of(s(20, "40", 9, 8), s(17, "40", 8, 7), s(14, "40", 8, 8), s(11, "40", 8, 8))))
                .isFalse();
        // Too few sessions to tell.
        assertThat(WorkoutCoachService.stalled(List.of(s(20, "40", 8), s(17, "40", 8), s(14, "40", 8)))).isFalse();
    }

    @Test
    void adviceForUnknownExercisesOrMusclesAlreadyCoveredIsDropped() {
        UUID bench = UUID.randomUUID();
        var answer = JsonMapper.builder().build().readTree("""
                {"note":"Go for 42.5 kg.",
                 "alerts":[{"exercise":"Bench press","type":"PLATEAU","message":"Try 5x5.","swapTo":""},
                           {"exercise":"Made up","type":"SWAP","message":"x","swapTo":"y"}],
                 "gaps":[{"muscle":"Back","message":"Add 4 rows."},{"muscle":"chest","message":"Covered."}]}
                """);
        var coaching = WorkoutCoachService.parse(answer, Map.of("bench press", bench), Map.of("chest", 12, "back", 3));
        assertThat(coaching.note()).isEqualTo("Go for 42.5 kg.");
        assertThat(coaching.alerts()).extracting(WorkoutCoachService.Alert::exerciseId).containsExactly(bench);
        assertThat(coaching.gaps()).extracting(WorkoutCoachService.Gap::muscle).containsExactly("back");
        assertThat(coaching.gaps().get(0).sets()).isEqualTo(3);
    }
}
