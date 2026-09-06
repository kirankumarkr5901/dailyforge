package com.dailyforge.points.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * How far the daily rollover job has settled a user's days (spec §5.6). A null
 * {@code lastProcessedDate} means "never run for this user" — everything up to and
 * including yesterday, in their zone, is unprocessed the first time the job sees them.
 */
@Entity
@Table(name = "user_rollover_state")
public class UserRolloverState {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "last_processed_date")
    private LocalDate lastProcessedDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRolloverState() {
        // for JPA
    }

    public static UserRolloverState fresh(UUID userId) {
        UserRolloverState state = new UserRolloverState();
        state.userId = userId;
        state.updatedAt = Instant.now();
        return state;
    }

    public void advanceTo(LocalDate date) {
        this.lastProcessedDate = date;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getLastProcessedDate() {
        return lastProcessedDate;
    }
}
