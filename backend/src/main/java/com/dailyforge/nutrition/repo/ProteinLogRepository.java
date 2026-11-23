package com.dailyforge.nutrition.repo;

import com.dailyforge.nutrition.domain.ProteinLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProteinLogRepository extends JpaRepository<ProteinLog, UUID> {

    List<ProteinLog> findAllByUserIdAndOccurredOnOrderByCreatedAtAsc(UUID userId, LocalDate occurredOn);

    Optional<ProteinLog> findByIdAndUserId(UUID id, UUID userId);
}
