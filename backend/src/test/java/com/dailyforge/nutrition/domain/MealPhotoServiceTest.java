package com.dailyforge.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.ai.GeminiProperties;
import com.dailyforge.common.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Turning Gemini's answer into dishes, and refusing input before anything is sent. No network. */
class MealPhotoServiceTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final MealPhotoService service =
            new MealPhotoService(new GeminiClient(new GeminiProperties("test-key", null, null), json));

    @Test
    void readsTheDishes() {
        var dishes = MealPhotoService.dishes(json.readTree("""
                {"dishes":[{"name":"Dal tadka","portionG":150,"proteinG":9.4,"kcal":180,"fatG":6,"carbsG":22,"fiberG":5,"sugarG":2}]}
                """));
        assertThat(dishes).containsExactly(new MealPhotoService.Dish("Dal tadka", 150, 9, 180, 6, 22, 5, 2));
    }

    @Test
    void clampsToTheAppsLimitsAndSkipsNamelessDishes() {
        var dishes = MealPhotoService.dishes(json.readTree("""
                {"dishes":[{"name":"Feast","portionG":900,"proteinG":250,"kcal":5000,"fatG":-3,"carbsG":80,"fiberG":4,"sugarG":10},
                           {"name":"  ","portionG":1}]}
                """));
        assertThat(dishes).containsExactly(new MealPhotoService.Dish("Feast", 900, 200, 3000, 0, 80, 4, 10));
    }

    @Test
    void onlyAJpegIsSent() {
        assertThatThrownBy(() -> service.analyze(UUID.randomUUID(), "aGVsbG8gd29ybGQ="))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("JPEG");
        assertThatThrownBy(() -> service.analyze(UUID.randomUUID(), "not base64 at all!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aDescriptionMustSaySomethingAndNotTooMuch() {
        assertThatThrownBy(() -> service.describe(UUID.randomUUID(), "   ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.describe(UUID.randomUUID(), "a".repeat(MealPhotoService.MAX_DESCRIPTION + 1)))
                .isInstanceOf(ApiException.class);
    }
}
