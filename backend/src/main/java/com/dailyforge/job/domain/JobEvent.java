package com.dailyforge.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One stage transition, kept forever — the auditable timeline the metrics compute from (spec §8.7). */
@Entity
@Table(name = "job_event")
public class JobEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private JobStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private JobStatus toStatus;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_stage", length = 20)
    private InterviewStage interviewStage;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobEvent() {
        // for JPA
    }

    public static JobEvent create(
            UUID applicationId,
            JobStatus fromStatus,
            JobStatus toStatus,
            Integer roundNumber,
            InterviewStage interviewStage,
            LocalDate occurredOn,
            String note) {
        JobEvent event = new JobEvent();
        event.id = UUID.randomUUID();
        event.applicationId = applicationId;
        event.fromStatus = fromStatus;
        event.toStatus = toStatus;
        event.roundNumber = roundNumber;
        event.interviewStage = interviewStage;
        event.occurredOn = occurredOn;
        event.note = note;
        return event;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public InterviewStage getInterviewStage() {
        return interviewStage;
    }

    public JobStatus getFromStatus() {
        return fromStatus;
    }

    public JobStatus getToStatus() {
        return toStatus;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
