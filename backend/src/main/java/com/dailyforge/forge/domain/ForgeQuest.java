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

    @Column(name = "template_code", nullable = false, length = 40)
    private String templateCode;

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
}
