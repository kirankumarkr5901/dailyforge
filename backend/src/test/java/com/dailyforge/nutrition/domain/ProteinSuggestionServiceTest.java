package com.dailyforge.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Turning the AI's suggestions into options to log. No network. */
class ProteinSuggestionServiceTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void aFoodFromTheListKeepsItsOwnNumbersAndOthersAreEstimates() {
        ProteinFood paneer = ProteinFood.create(UUID.randomUUID(), "Paneer 100 g", 18, 265, 20, 0);
        var answer = json.readTree("""
                {"suggestions":[
                  {"name":"paneer 100 g","fromTheirFoods":true,"why":"Quick protein.","proteinG":99,"kcal":1,"fatG":1,"carbsG":0,"fiberG":0,"sugarG":0},
                  {"name":"Boiled chana, 1 cup","fromTheirFoods":false,"why":"Fiber too.","proteinG":15,"kcal":270,"fatG":4,"carbsG":45,"fiberG":12,"sugarG":8},
                  {"name":"Nothing","fromTheirFoods":false,"why":"","proteinG":0,"kcal":0,"fatG":0,"carbsG":0,"fiberG":0,"sugarG":0}
                ]}
                """);
        var out = ProteinSuggestionService.suggestions(answer, List.of(paneer));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).foodId()).isEqualTo(paneer.getId());
        assertThat(out.get(0).proteinG()).isEqualTo(18);
        assertThat(out.get(0).name()).isEqualTo("Paneer 100 g");
        assertThat(out.get(1).foodId()).isNull();
        assertThat(out.get(1).fiberG()).isEqualTo(12);
    }
}
