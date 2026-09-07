package com.dailyforge.job.repo;

import com.dailyforge.job.domain.JobEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    List<JobEvent> findAllByApplicationIdOrderByOccurredOnAscCreatedAtAsc(UUID applicationId);

    /** Every event for every application this user has — the metrics compute from this in one pass. */
    List<JobEvent> findAllByApplicationIdIn(List<UUID> applicationIds);
}
