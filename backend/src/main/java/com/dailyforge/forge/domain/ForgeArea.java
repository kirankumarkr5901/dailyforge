package com.dailyforge.forge.domain;

/**
 * The six life areas Forge 66 works on. The questionnaire scores them, the plan draws
 * quests from the strongest needs, and each area doubles as a stat that grows with the
 * XP its quests earn. Mirrors the CHECK constraint on {@code forge_quest_template.area}.
 */
public enum ForgeArea {
    FITNESS,
    FOCUS,
    HEALTH,
    MINDSET,
    LEARNING,
    SOCIAL
}
