package com.dailyforge.workout.domain;

import com.dailyforge.workout.repo.ExerciseRepository;
import com.dailyforge.workout.repo.WorkoutSetRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard sets per muscle for one Monday-to-Sunday week: the usual way to check whether
 * each muscle gets enough work (about 10 to 20 sets a week is the common target).
 *
 * <p>Each set counts once, toward its exercise's primary (first-listed) muscle group,
 * the same group the workout board files the exercise under. Counting a bench set
 * toward chest, shoulders and triceps alike would inflate every number. Cardio and
 * exercises with no muscle groups are left out.
 */
@Service
public class WeeklyVolumeService {

    /** The usual weekly target band per muscle. */
    public static final int TARGET_LOW = 10;
    public static final int TARGET_HIGH = 20;

    private final WorkoutSetRepository sets;
    private final ExerciseRepository exercises;

    public WeeklyVolumeService(WorkoutSetRepository sets, ExerciseRepository exercises) {
        this.sets = sets;
        this.exercises = exercises;
    }

    public record MuscleSets(String group, int sets) {}

    public record WeeklyVolume(LocalDate weekStart, LocalDate weekEnd, List<MuscleSets> muscles) {}

    public static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    @Transactional(readOnly = true)
    public WeeklyVolume forWeekOf(UUID userId, LocalDate date) {
        LocalDate start = mondayOf(date);
        LocalDate end = start.plusDays(6);
        List<WorkoutSet> logged = sets.findAllForUserBetween(userId, start, end);

        Set<UUID> ids = logged.stream().map(WorkoutSet::getExerciseId).collect(Collectors.toSet());
        Map<UUID, Exercise> byId =
                exercises.findAllById(ids).stream().collect(Collectors.toMap(Exercise::getId, e -> e));

        Map<String, Integer> counts = new HashMap<>();
        for (WorkoutSet set : logged) {
            Exercise exercise = byId.get(set.getExerciseId());
            if (exercise == null || exercise.getKind() == ExerciseKind.CARDIO || exercise.getMuscleGroups().isEmpty()) {
                continue;
            }
            counts.merge(exercise.getMuscleGroups().get(0).trim().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }
        List<MuscleSets> muscles = counts.entrySet().stream()
                .map(e -> new MuscleSets(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(MuscleSets::sets).reversed().thenComparing(MuscleSets::group))
                .toList();
        return new WeeklyVolume(start, end, muscles);
    }
}
