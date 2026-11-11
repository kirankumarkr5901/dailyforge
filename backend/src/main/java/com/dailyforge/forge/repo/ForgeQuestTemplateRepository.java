package com.dailyforge.forge.repo;

import com.dailyforge.forge.domain.ForgeQuestTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForgeQuestTemplateRepository extends JpaRepository<ForgeQuestTemplate, UUID> {

    List<ForgeQuestTemplate> findAllByOrderByCodeAsc();

    java.util.Optional<ForgeQuestTemplate> findByCode(String code);
}
