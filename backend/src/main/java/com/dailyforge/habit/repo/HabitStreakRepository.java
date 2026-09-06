package com.dailyforge.habit.repo;

import com.dailyforge.habit.domain.HabitStreak;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitStreakRepository extends JpaRepository<HabitStreak, UUID> {
}
