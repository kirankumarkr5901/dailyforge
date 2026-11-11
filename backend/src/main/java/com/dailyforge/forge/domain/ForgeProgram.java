package com.dailyforge.forge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** One 66-day run (see V26__forge66.sql). */
@Entity
@Table(name = "forge_program")
public class ForgeProgram {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private ForgeMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ForgeProgramStatus status;

    @Column(name = "focus_areas", nullable = false, length = 100)
    private String focusAreas;

    @Column(name = "answers_json", nullable = false, length = 2000)
    private String answersJson;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "active_user_id")
    private UUID activeUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ForgeProgram() {
        // for JPA
    }

    public static ForgeProgram start(
            UUID userId,
            LocalDate startDate,
            int lengthDays,
            ForgeMode mode,
            List<ForgeArea> focusAreas,
            String answersJson) {
        ForgeProgram program = new ForgeProgram();
        program.id = UUID.randomUUID();
        program.userId = userId;
        program.startDate = startDate;
        program.endDate = startDate.plusDays(lengthDays - 1L);
        program.mode = mode;
        program.status = ForgeProgramStatus.ACTIVE;
        program.focusAreas = focusAreas.stream().map(Enum::name).collect(Collectors.joining(","));
        program.answersJson = answersJson;
        program.activeUserId = userId;
        return program;
    }

    /** The last day ran out. Days are never cut short — this only records that it is over. */
    public void complete() {
        status = ForgeProgramStatus.COMPLETED;
        activeUserId = null;
    }

    /** Stops the program from {@code today} onward; days already closed keep their history. */
    public void abandon(LocalDate today) {
        status = ForgeProgramStatus.ABANDONED;
        endedOn = today;
        activeUserId = null;
    }

    /**
     * The last day that counts for this program: its own last day, or the day before it
     * was abandoned. Can fall before {@link #getStartDate()} for a program abandoned on
     * its first day, in which case no day counts at all.
     */
    public LocalDate lastCountedDay() {
        if (endedOn != null && endedOn.minusDays(1).isBefore(endDate)) {
            return endedOn.minusDays(1);
        }
        return endDate;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ForgeMode getMode() {
        return mode;
    }

    public ForgeProgramStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == ForgeProgramStatus.ACTIVE;
    }

    public List<ForgeArea> getFocusAreas() {
        return Arrays.stream(focusAreas.split(",")).map(ForgeArea::valueOf).toList();
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
