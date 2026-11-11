package com.dailyforge.forge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One entry in the seeded quest catalogue (V26__forge66.sql). Read-only from Java. */
@Entity
@Table(name = "forge_quest_template")
public class ForgeQuestTemplate {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", nullable = false, length = 12)
    private ForgeArea area;

    @Column(name = "tier", nullable = false)
    private int tier;

    @Column(name = "title", nullable = false, length = 80)
    private String title;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    @Column(name = "base_xp", nullable = false)
    private int baseXp;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected ForgeQuestTemplate() {
        // for JPA
    }

    /** For pure tests of the plan generator; real rows only ever come from the migration. */
    public static ForgeQuestTemplate of(String code, ForgeArea area, int tier, int baseXp) {
        ForgeQuestTemplate template = new ForgeQuestTemplate();
        template.id = UUID.randomUUID();
        template.code = code;
        template.area = area;
        template.tier = tier;
        template.title = code;
        template.description = code;
        template.baseXp = baseXp;
        template.active = true;
        return template;
    }

    public String getCode() {
        return code;
    }

    public ForgeArea getArea() {
        return area;
    }

    public int getTier() {
        return tier;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getBaseXp() {
        return baseXp;
    }

    public boolean isActive() {
        return active;
    }
}
