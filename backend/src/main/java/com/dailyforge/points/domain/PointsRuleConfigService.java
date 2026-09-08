package com.dailyforge.points.domain;

import com.dailyforge.points.repo.PointsRuleConfigRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@code points_rule_config}: a user override first, the system default
 * otherwise. This is the only path by which a point value reaches code (spec §5.4, and
 * PRODUCT.md's invariant 6) — nothing in the points, habit, workout or run modules
 * may write a numeric literal where a rule's amount belongs.
 */
@Service
public class PointsRuleConfigService {

    private final PointsRuleConfigRepository repository;
    private final ObjectMapper objectMapper;

    public PointsRuleConfigService(PointsRuleConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public RuleConfig get(String ruleCode, UUID userId) {
        PointsRuleConfig config =
                repository
                        .findByUserIdAndRuleCode(userId, ruleCode)
                        .or(() -> repository.findByUserIdIsNullAndRuleCode(ruleCode))
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No points_rule_config row for rule_code '"
                                                        + ruleCode
                                                        + "'. Every rule must be seeded by migration."));

        JsonNode node = objectMapper.readTree(config.getConfigJson());
        return new RuleConfig(ruleCode, node, config.isEnabled());
    }

    /** The system default, ignoring any per-user override — used by guardrails that apply to everyone. */
    public RuleConfig getSystemDefault(String ruleCode) {
        PointsRuleConfig config =
                repository
                        .findByUserIdIsNullAndRuleCode(ruleCode)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No system default points_rule_config row for rule_code '"
                                                        + ruleCode
                                                        + "'."));
        return new RuleConfig(ruleCode, objectMapper.readTree(config.getConfigJson()), config.isEnabled());
    }
}
