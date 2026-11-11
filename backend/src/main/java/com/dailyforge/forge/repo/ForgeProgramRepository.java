package com.dailyforge.forge.repo;

import com.dailyforge.forge.domain.ForgeProgram;
import com.dailyforge.forge.domain.ForgeProgramStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForgeProgramRepository extends JpaRepository<ForgeProgram, UUID> {

    Optional<ForgeProgram> findFirstByUserIdAndStatus(UUID userId, ForgeProgramStatus status);

    List<ForgeProgram> findAllByUserIdOrderByStartDateAscCreatedAtAsc(UUID userId);
}
