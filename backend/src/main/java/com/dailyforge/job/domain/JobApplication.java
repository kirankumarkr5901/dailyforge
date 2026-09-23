package com.dailyforge.job.domain;

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
import java.util.UUID;

/** One application through the pipeline (spec §8.7). */
@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company", nullable = false, length = 120)
    private String company;

    @Column(name = "role", nullable = false, length = 120)
    private String role;

    @Column(name = "role_id", length = 80)
    private String roleId;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "job_url", length = 500)
    private String jobUrl;

    @Column(name = "resume_version", length = 80)
    private String resumeVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private JobSource source;

    @Column(name = "referrer_name", length = 120)
    private String referrerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "current_round", nullable = false)
    private int currentRound;

    @Column(name = "next_follow_up_on")
    private LocalDate nextFollowUpOn;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "applied_on", nullable = false)
    private LocalDate appliedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
        // for JPA
    }

    public static JobApplication create(
            UUID userId,
            String company,
            String role,
            String roleId,
            String city,
            String jobUrl,
            String resumeVersion,
            JobSource source,
            String referrerName,
            String note,
            LocalDate appliedOn) {
        JobApplication app = new JobApplication();
        app.id = UUID.randomUUID();
        app.userId = userId;
        app.company = company;
        app.role = role;
        app.roleId = roleId;
        app.city = city;
        app.jobUrl = jobUrl;
        app.resumeVersion = resumeVersion;
        app.source = source;
        app.referrerName = referrerName;
        app.note = note;
        app.appliedOn = appliedOn;
        app.status = JobStatus.APPLIED;
        app.currentRound = 0;
        return app;
    }

    public void update(
            String company,
            String role,
            String roleId,
            String city,
            String jobUrl,
            String resumeVersion,
            String referrerName,
            String note,
            LocalDate nextFollowUpOn) {
        if (company != null) this.company = company;
        if (role != null) this.role = role;
        if (roleId != null) this.roleId = roleId;
        if (city != null) this.city = city;
        if (jobUrl != null) this.jobUrl = jobUrl;
        if (resumeVersion != null) this.resumeVersion = resumeVersion;
        if (referrerName != null) this.referrerName = referrerName;
        if (note != null) this.note = note;
        this.nextFollowUpOn = nextFollowUpOn;
    }

    public void transitionTo(JobStatus toStatus, Integer roundNumber) {
        this.status = toStatus;
        if (roundNumber != null) {
            this.currentRound = roundNumber;
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getCity() {
        return city;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public String getResumeVersion() {
        return resumeVersion;
    }

    public JobSource getSource() {
        return source;
    }

    public String getReferrerName() {
        return referrerName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public LocalDate getNextFollowUpOn() {
        return nextFollowUpOn;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }
}
