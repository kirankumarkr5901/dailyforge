package com.dailyforge.body.repo;

import com.dailyforge.body.domain.BodyMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, UUID> {

    Optional<BodyMetric> findByUserIdAndOccurredOn(UUID userId, LocalDate occurredOn);

    Optional<BodyMetric> findByIdAndUserId(UUID id, UUID userId);

    List<BodyMetric> findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(UUID userId, LocalDate from, LocalDate to);

    List<BodyMetric> findAllByUserIdOrderByOccurredOnDesc(UUID userId);

    /** The most recent entry at or before a date — a goal's progress reads this (spec §8.6). */
    Optional<BodyMetric> findFirstByUserIdAndOccurredOnLessThanEqualOrderByOccurredOnDesc(UUID userId, LocalDate onOrBefore);
}
