package com.dailyforge.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Reading Gemini's answer, the fallback model and the daily allowance. No network. */
class GeminiClientTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final GeminiClient client = new GeminiClient(new GeminiProperties("test-key", null, null), json);

    @Test
    void readsTheAnswerFromOutputText() {
        var answer = client.answer(json.readTree("{\"output_text\": \"{\\\"a\\\": 1}\"}"));
        assertThat(answer.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheModelOutputStepAndDropsCodeFences() {
        var answer = client.answer(json.readTree("""
                {"steps": [
                  {"type": "thought", "content": [{"type": "text", "text": "ignored"}]},
                  {"type": "model_output", "content": [{"type": "text", "text": "```json {\\"a\\": 2} ```"}]}
                ]}
                """));
        assertThat(answer.get("a").asInt()).isEqualTo(2);
    }

    @Test
    void anAnswerThatIsNotJsonIsRefused() {
        assertThatThrownBy(() -> client.answer(json.readTree("{\"output_text\": \"I see rice.\"}")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void eachFeatureHasItsOwnDailyAllowance() {
        UUID user = UUID.randomUUID();
        client.countUse(user, "x", 2, "tries");
        client.countUse(user, "x", 2, "tries");
        client.countUse(user, "y", 2, "tries");
        assertThatThrownBy(() -> client.countUse(user, "x", 2, "tries"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("2 tries");
    }

    /** A busy model falls back to the other one, never to itself; only server trouble is retried. */
    @Test
    void theFallbackIsTheOtherModel() {
        assertThat(new GeminiProperties("k", null, null).fallbackModel()).isEqualTo(GeminiProperties.DEFAULT_FALLBACK_MODEL);
        assertThat(new GeminiProperties("k", "gemini-3.8-flash", null).fallbackModel()).isEqualTo(GeminiProperties.DEFAULT_MODEL);
        assertThat(new GeminiProperties("k", "a", "b").fallbackModel()).isEqualTo("b");
        assertThat(GeminiClient.retryable(503)).isTrue();
        assertThat(GeminiClient.retryable(429)).isFalse();
        assertThat(GeminiClient.retryable(400)).isFalse();
    }

    @Test
    void withoutAKeyItIsOff() {
        var off = new GeminiClient(new GeminiProperties("  ", "", ""), json);
        assertThat(off.enabled()).isFalse();
        assertThatThrownBy(off::requireEnabled).isInstanceOf(ApiException.class);
    }
}
