package com.dailyforge.nutrition.repo;

import com.dailyforge.nutrition.domain.NutritionSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionSettingsRepository extends JpaRepository<NutritionSettings, UUID> {}
