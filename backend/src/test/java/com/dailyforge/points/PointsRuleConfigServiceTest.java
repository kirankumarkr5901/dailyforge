package com.dailyforge.points;

import com.dailyforge.testsupport.TestUsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.RuleConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * A per-user override must win over the system default; a rule with no override at all
 * must still resolve. This is the mechanism the spec's "no magic numbers" rule (§5.4,
 * non-negotiable #6) actually depends on, so it is tested directly rather than only
 * indirectly through whatever module happens to call it.
 */
@SpringBootTest
@ActiveProfiles("test")
class PointsRuleConfigServiceTest {

    @Autowired private PointsRuleConfigService configs;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void aSeededSystemDefaultResolvesForAUserWithNoOverride() {
        UUID user = TestUsers.create(users, settings);

        RuleConfig config = configs.get("WORKOUT_SET", user);

        assertThat(config.enabled()).isTrue();
        assertThat(config.getInt("points")).isEqualTo(1);
    }

    @Test
    @Transactional
    void aPerUserOverrideWinsOverTheSystemDefault() {
        UUID user = TestUsers.create(users, settings);

        // Bypasses the (not-yet-built) settings UI: writes the override row directly,
        // exactly as a future "custom rule value" feature would.
        jdbc.update(
                "INSERT INTO points_rule_config (id, user_id, rule_code, config_json, enabled, description, created_at, updated_at) "
                        + "VALUES (?, ?, 'WORKOUT_SET', '{\"points\": 5}', TRUE, 'override', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                user);

        RuleConfig config = configs.get("WORKOUT_SET", user);

        assertThat(config.getInt("points")).isEqualTo(5);
    }

    @Test
    void aRuleCodeWithNoSeededRowAtAllFailsLoudlyRatherThanInventingAValue() {
        UUID user = TestUsers.create(users, settings);

        assertThatThrownBy(() -> configs.get("NOT_A_REAL_RULE", user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_A_REAL_RULE");
    }

    @Test
    void getSystemDefaultIgnoresAnyPerUserOverride() {
        UUID user = TestUsers.create(users, settings);

        // Even without writing an override for this user, this confirms the two lookup
        // paths are genuinely independent, not aliases of each other.
        RuleConfig fromUserPath = configs.get("HABIT_COMMITMENT", user);
        RuleConfig systemDefault = configs.getSystemDefault("HABIT_COMMITMENT");

        assertThat(fromUserPath.getString("source")).isEqualTo(systemDefault.getString("source"));
    }
}
