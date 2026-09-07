package com.dailyforge.activity.repo;

import com.dailyforge.activity.domain.ActivityLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    Optional<ActivityLog> findByIdAndUserId(UUID id, UUID userId);

    List<ActivityLog> findAllByUserIdOrderByOccurredOnDescCreatedAtDesc(UUID userId);

    List<ActivityLog> findAllByActivityTypeIdOrderByOccurredOnDesc(UUID activityTypeId);
}
