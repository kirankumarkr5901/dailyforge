package com.dailyforge.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import com.dailyforge.workout.domain.Equipment;
import com.dailyforge.workout.domain.ExerciseKind;
import com.dailyforge.workout.domain.ExerciseService;
import com.dailyforge.workout.domain.WeeklyVolumeService;
import com.dailyforge.workout.domain.WeeklyVolumeService.MuscleSets;
import com.dailyforge.workout.domain.WeightMode;
import com.dailyforge.workout.domain.WorkoutSetService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Sets per primary muscle for one Monday-to-Sunday week. */
@SpringBootTest
@ActiveProfiles("test")
class WeeklyVolumeTest {

    @Autowired private WeeklyVolumeService volume;
    @Autowired private WorkoutSetService setService;
    @Autowired private ExerciseService exercises;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private UUID user;

    @BeforeEach
    void setUp() {
        user = TestUsers.create(users, settings);
    }

    private UUID exercise(ExerciseKind kind, List<String> groups) {
        return exercises.create(user, "Ex " + UUID.randomUUID(), kind, Equipment.BARBELL, groups, false).getId();
    }

    private void log(UUID exerciseId, int count) {
        for (int i = 0; i < count; i++) {
            setService.logSet(user, TODAY, exerciseId, null, null, new BigDecimal("50"), WeightMode.COMBINED, null, 8);
        }
    }

    @Test
    void eachSetCountsOnceTowardItsPrimaryMuscle() {
        log(exercise(ExerciseKind.STRENGTH, List.of("chest", "triceps")), 4);
        log(exercise(ExerciseKind.STRENGTH, List.of("Chest")), 3);
        log(exercise(ExerciseKind.STRENGTH, List.of("back")), 2);

        var week = volume.forWeekOf(user, TODAY);

        assertThat(week.muscles()).containsExactly(new MuscleSets("chest", 7), new MuscleSets("back", 2));
        assertThat(week.weekStart()).isEqualTo(WeeklyVolumeService.mondayOf(TODAY));
        assertThat(week.weekEnd()).isEqualTo(week.weekStart().plusDays(6));
    }

    @Test
    void cardioIsLeftOut() {
        log(exercise(ExerciseKind.CARDIO, List.of("legs")), 1);

        assertThat(volume.forWeekOf(user, TODAY).muscles()).isEmpty();
    }

    @Test
    void anotherWeekSeesNothing() {
        log(exercise(ExerciseKind.STRENGTH, List.of("chest")), 3);

        assertThat(volume.forWeekOf(user, TODAY.plusWeeks(1)).muscles()).isEmpty();
    }

    @Test
    void mondayIsTheStartOfTheWeek() {
        assertThat(WeeklyVolumeService.mondayOf(LocalDate.of(2026, 11, 29))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(WeeklyVolumeService.mondayOf(LocalDate.of(2026, 11, 23))).isEqualTo(LocalDate.of(2026, 11, 23));
    }
}
