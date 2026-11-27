package com.dailyforge.forge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One quest on one day of a program. Its only mutable state is whether it was done. */
@Entity
@Table(name = "forge_quest")
public class ForgeQuest {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quest_date", nullable = false)
    private LocalDate questDate;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    /** Null for a quest the AI wrote; then title, description, area and tier are its own. */
    @Column(name = "template_code", length = 40)
    private String templateCode;

    @Column(name = "title", length = 80)
    private String title;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "area", length = 12)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private ForgeArea area;

    @Column(name = "tier")
    private Integer tier;

    @Column(name = "xp", nullable = false)
    private int xp;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ForgeQuest() {
        // for JPA
    }

    public static ForgeQuest planned(
            UUID programId, UUID userId, LocalDate questDate, int dayNumber, String templateCode, int xp) {
        ForgeQuest quest = new ForgeQuest();
        quest.id = UUID.randomUUID();
        quest.programId = programId;
        quest.userId = userId;
        quest.questDate = questDate;
        quest.dayNumber = dayNumber;
        quest.templateCode = templateCode;
        quest.xp = xp;
        return quest;
    }

    /** A quest written by the AI for this person: no template, its own words. */
    public static ForgeQuest custom(
            UUID programId, UUID userId, LocalDate questDate, int dayNumber,
            String title, String description, ForgeArea area, int tier, int xp) {
        ForgeQuest quest = planned(programId, userId, questDate, dayNumber, null, xp);
        quest.title = title;
        quest.description = description;
        quest.area = area;
        quest.tier = tier;
        return quest;
    }

    public void markDone(Instant at) {
        completedAt = at;
    }

    public void markNotDone() {
        completedAt = null;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProgramId() {
        return programId;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getQuestDate() {
        return questDate;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public int getXp() {
        return xp;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isDone() {
        return completedAt != null;
    }

    public boolean isCustom() {
        return templateCode == null;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ForgeArea getArea() {
        return area;
    }

    public Integer getTier() {
        return tier;
    }
}
