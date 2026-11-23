package com.dailyforge.nutrition.repo;

import com.dailyforge.nutrition.domain.ProteinFood;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProteinFoodRepository extends JpaRepository<ProteinFood, UUID> {

    List<ProteinFood> findAllByUserIdOrderBySortOrderAscNameAsc(UUID userId);

    Optional<ProteinFood> findByIdAndUserId(UUID id, UUID userId);
}
