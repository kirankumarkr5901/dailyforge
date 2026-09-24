package com.dailyforge.job.repo;

import com.dailyforge.job.domain.JobApplication;
import com.dailyforge.job.domain.JobSource;
import com.dailyforge.job.domain.JobStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    List<JobApplication> findAllByUserIdOrderByAppliedOnDesc(UUID userId);

    List<JobApplication> findAllByUserIdAndStatusOrderByAppliedOnDesc(UUID userId, JobStatus status);

    List<JobApplication> findAllByUserIdAndNextFollowUpOnLessThanEqual(UUID userId, LocalDate onOrBefore);

    /**
     * The referral list: every application whose source says a referral was involved.
     *
     * Oldest request first, because the list is read to find what has been waiting
     * longest — the one thing a referral list is actually for. Nulls sort last under
     * this ordering, which is right: a referral with no recorded ask date has no claim
     * on the top of a queue ordered by waiting time.
     */
    List<JobApplication> findAllByUserIdAndSourceInOrderByReferralRequestedOnAsc(
            UUID userId, java.util.Collection<JobSource> sources);
}
