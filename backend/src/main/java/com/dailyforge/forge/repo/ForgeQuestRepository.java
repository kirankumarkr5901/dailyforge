package com.dailyforge.forge.repo;

import com.dailyforge.forge.domain.ForgeQuest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForgeQuestRepository extends JpaRepository<ForgeQuest, UUID> {

    Optional<ForgeQuest> findByIdAndUserId(UUID id, UUID userId);

    List<ForgeQuest> findAllByUserIdOrderByQuestDateAsc(UUID userId);

    List<ForgeQuest> findAllByProgramIdAndQuestDate(UUID programId, java.time.LocalDate questDate);
}
