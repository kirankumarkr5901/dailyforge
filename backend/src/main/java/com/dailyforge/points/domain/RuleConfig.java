package com.dailyforge.points.domain;

import tools.jackson.databind.JsonNode;

/**
 * A parsed {@code points_rule_config.config_json} row, with typed accessors.
 *
 * Every accessor requires the field it reads — a missing key throws rather than
 * silently defaulting to zero, because a silent zero for a mistyped config key is
 * indistinguishable from a deliberate "no points for this" and would take real user
 * reports to notice.
 */
public final class RuleConfig {

    private final String ruleCode;
    private final JsonNode node;
    private final boolean enabled;

    RuleConfig(String ruleCode, JsonNode node, boolean enabled) {
        this.ruleCode = ruleCode;
        this.node = node;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public int getInt(String field) {
        return require(field).asInt();
    }

    public double getDouble(String field) {
        return require(field).asDouble();
    }

    public String getString(String field) {
        return require(field).asString();
    }

    public boolean getBoolean(String field) {
        return require(field).asBoolean();
    }

    public JsonNode getNode(String field) {
        return require(field);
    }

    /** For a config field that may legitimately be absent or null (e.g. an uncapped guardrail). */
    public Integer getIntOrNull(String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private JsonNode require(String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(
                    "points_rule_config for '" + ruleCode + "' is missing required field '" + field + "'");
        }
        return value;
    }
}
