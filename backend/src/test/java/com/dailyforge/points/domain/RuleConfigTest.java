package com.dailyforge.points.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every point value in the app is read through this class (spec §5.4, non-negotiable
 * #6). A silent wrong answer here — a missing field defaulting to zero, say — would be
 * indistinguishable from "no points for this" and would take a user report to notice, so
 * the required-field behaviour is tested directly rather than trusted.
 */
class RuleConfigTest {

    private static final ObjectMapper json = new ObjectMapper();

    private RuleConfig configFor(String jsonText, boolean enabled) {
        return new RuleConfig("TEST_RULE", json.readTree(jsonText), enabled);
    }

    @Test
    void readsEachTypedFieldCorrectly() {
        RuleConfig config =
                configFor("{\"points\": 12, \"multiplier\": 1.5, \"label\": \"x\", \"on\": true}", true);

        assertThat(config.getInt("points")).isEqualTo(12);
        assertThat(config.getDouble("multiplier")).isEqualTo(1.5);
        assertThat(config.getString("label")).isEqualTo("x");
        assertThat(config.getBoolean("on")).isTrue();
        assertThat(config.enabled()).isTrue();
    }

    @Test
    void aMissingRequiredFieldThrowsRatherThanSilentlyDefaultingToZero() {
        RuleConfig config = configFor("{\"points\": 12}", true);

        assertThatThrownBy(() -> config.getInt("penaltyPoints"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("penaltyPoints")
                .hasMessageContaining("TEST_RULE");
    }

    @Test
    void aNullFieldIsTreatedTheSameAsMissingForRequiredAccess() {
        RuleConfig config = configFor("{\"points\": null}", true);

        assertThatThrownBy(() -> config.getInt("points")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getIntOrNullReturnsNullForAnAbsentOrNullField() {
        RuleConfig config = configFor("{\"cap\": null}", true);

        assertThat(config.getIntOrNull("cap")).isNull();
        assertThat(config.getIntOrNull("notPresentAtAll")).isNull();
    }

    @Test
    void getIntOrNullReturnsTheValueWhenPresent() {
        RuleConfig config = configFor("{\"cap\": 500}", true);

        assertThat(config.getIntOrNull("cap")).isEqualTo(500);
    }

    @Test
    void getNodeReturnsTheRawNodeForNestedStructures() {
        RuleConfig config = configFor("{\"milestones\": [{\"metres\": 10000, \"points\": 50}]}", true);

        JsonNode milestones = config.getNode("milestones");
        assertThat(milestones.isArray()).isTrue();
        assertThat(milestones.get(0).get("points").asInt()).isEqualTo(50);
    }

    @Test
    void disabledIsReadFromTheEnabledFlagNotFromTheJson() {
        RuleConfig config = configFor("{\"points\": 0}", false);

        assertThat(config.enabled()).isFalse();
    }
}
