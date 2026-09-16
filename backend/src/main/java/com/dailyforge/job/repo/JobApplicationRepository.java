package com.dailyforge.job.repo;

import com.dailyforge.job.domain.JobApplication;
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
}
