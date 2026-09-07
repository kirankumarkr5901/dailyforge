package com.dailyforge.activity.repo;

import com.dailyforge.activity.domain.ActivityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityTypeRepository extends JpaRepository<ActivityType, UUID> {

    Optional<ActivityType> findByIdAndUserId(UUID id, UUID userId);

    List<ActivityType> findAllByUserIdAndArchivedAtIsNullOrderBySortOrderAsc(UUID userId);
}
