package com.dailyforge.nutrition.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Reading Gemini's answer, and refusing a photo before anything is sent. No network. */
class MealPhotoServiceTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final MealPhotoService service = new MealPhotoService(new GeminiProperties("test-key", null, null), json);

    @Test
    void readsTheDishesFromOutputText() {
        var interaction = json.readTree("""
                {"output_text": "{\\"dishes\\":[{\\"name\\":\\"Dal tadka\\",\\"portionG\\":150,\\"proteinG\\":9.4,\
                \\"kcal\\":180,\\"fatG\\":6,\\"carbsG\\":22,\\"fiberG\\":5,\\"sugarG\\":2}]}"}
                """);
        var dishes = service.parse(interaction);
        assertThat(dishes).containsExactly(new MealPhotoService.Dish("Dal tadka", 150, 9, 180, 6, 22, 5, 2));
    }

    @Test
    void fallsBackToTheModelOutputStepAndClampsToTheAppsLimits() {
        var interaction = json.readTree("""
                {"steps": [
                  {"type": "thought", "content": [{"type": "text", "text": "ignored"}]},
                  {"type": "model_output", "content": [{"type": "text", "text": "```json\\n{\\"dishes\\":[\
                {\\"name\\":\\"Feast\\",\\"portionG\\":900,\\"proteinG\\":250,\\"kcal\\":5000,\\"fatG\\":-3,\
                \\"carbsG\\":80,\\"fiberG\\":4,\\"sugarG\\":10},{\\"name\\":\\"  \\",\\"portionG\\":1}]}\\n```"}]}
                ]}
                """);
        var dishes = service.parse(interaction);
        assertThat(dishes).containsExactly(new MealPhotoService.Dish("Feast", 900, 200, 3000, 0, 80, 4, 10));
    }

    @Test
    void anAnswerThatIsNotJsonIsABadGateway() {
        assertThatThrownBy(() -> service.parse(json.readTree("{\"output_text\": \"I see rice.\"}")))
                .isInstanceOf(ApiException.class);
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
    void withoutAKeyItIsOff() {
        var off = new MealPhotoService(new GeminiProperties("  ", "", ""), json);
        assertThat(off.enabled()).isFalse();
        assertThat(new GeminiProperties("k", "", null).model()).isEqualTo(GeminiProperties.DEFAULT_MODEL);
    }

    /** A busy model falls back to the other one, never to itself; only server trouble is retried. */
    @Test
    void theFallbackIsTheOtherModel() {
        assertThat(new GeminiProperties("k", null, null).fallbackModel()).isEqualTo(GeminiProperties.DEFAULT_FALLBACK_MODEL);
        assertThat(new GeminiProperties("k", "gemini-3.8-flash", null).fallbackModel()).isEqualTo(GeminiProperties.DEFAULT_MODEL);
        assertThat(new GeminiProperties("k", "a", "b").fallbackModel()).isEqualTo("b");
        assertThat(MealPhotoService.retryable(503)).isTrue();
        assertThat(MealPhotoService.retryable(429)).isFalse();
        assertThat(MealPhotoService.retryable(400)).isFalse();
    }
}
