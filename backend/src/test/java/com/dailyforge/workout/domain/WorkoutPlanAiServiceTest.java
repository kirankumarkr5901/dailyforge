package com.dailyforge.workout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Checking the AI's plan before it's shown. No network. */
class WorkoutPlanAiServiceTest {

    @Test
    void libraryExercisesKeepTheirIdsSoHistoryCarriesOnAndNumbersAreClamped() {
        Exercise bench = Exercise.create(null, "Bench Press", ExerciseKind.STRENGTH, Equipment.BARBELL, List.of("chest"), false);
        var a = JsonMapper.builder().build().readTree("""
                {"name":"Upper Lower","summary":"Four days.","days":[
                  {"label":"Upper A","exercises":[
                    {"name":"bench press","muscle":"chest","equipment":"BARBELL","sets":40,"repsMin":12,"repsMax":8,"restSeconds":5},
                    {"name":"Bench Press","muscle":"chest","equipment":"BARBELL","sets":3,"repsMin":8,"repsMax":12,"restSeconds":90},
                    {"name":"Landmine press","muscle":"shoulders","equipment":"LASER","sets":3,"repsMin":8,"repsMax":12,"restSeconds":90}]},
                  {"label":"","exercises":[]}]}
                """);
        var draft = WorkoutPlanAiService.parse(a, List.of(bench));
        assertThat(draft.days()).hasSize(1);
        var first = draft.days().get(0).exercises().get(0);
        assertThat(first.exerciseId()).isEqualTo(bench.getId());
        assertThat(first.name()).isEqualTo("Bench Press");
        assertThat(first.sets()).isEqualTo(10);
        assertThat(first.repsMax()).isGreaterThanOrEqualTo(first.repsMin());
        assertThat(first.restSeconds()).isEqualTo(15);
        var added = draft.days().get(0).exercises().get(1);
        assertThat(added.exerciseId()).isNull();
        assertThat(added.equipment()).isEqualTo(Equipment.NONE);
        assertThat(draft.days().get(0).exercises()).hasSize(2); // the repeat was dropped
    }
}
