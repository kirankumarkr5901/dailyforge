package com.dailyforge.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.activity.domain.ActivityPolarity;
import com.dailyforge.activity.domain.ActivityService;
import com.dailyforge.activity.domain.ActivityType;
import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Positive and negative one-off activities (spec §6 "activity") — a direct
 * source-tracked award per log, reversed on delete, no reconciliation engine (there is
 * no history to recompute against, the same reasoning a reward redemption uses).
 */
@SpringBootTest
@ActiveProfiles("test")
class ActivityServiceTest {

    @Autowired private ActivityService activities;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 12);

    @Test
    void loggingAPositiveActivityAwardsItsPoints() {
        UUID user = TestUsers.create(users, settings);
        ActivityType meditated = activities.create(user, "Meditated", ActivityPolarity.POSITIVE, 5, "sparkles");

        var result = activities.log(meditated.getId(), user, TODAY, 1, null);

        assertThat(result.points().delta()).isEqualTo(5);
        assertThat(result.points().newTotal()).isEqualTo(5);
    }

    @Test
    void loggingANegativeActivitySubtractsItsPoints() {
        UUID user = TestUsers.create(users, settings);
        ActivityType skipped = activities.create(user, "Skipped a workout", ActivityPolarity.NEGATIVE, 10, "frown");

        var result = activities.log(skipped.getId(), user, TODAY, 1, null);

        assertThat(result.points().delta()).isEqualTo(-10);
        assertThat(result.points().newTotal()).isEqualTo(-10);
    }

    @Test
    void countMultipliesThePointsAwarded() {
        UUID user = TestUsers.create(users, settings);
        ActivityType meditated = activities.create(user, "Meditated", ActivityPolarity.POSITIVE, 5, "sparkles");

        var result = activities.log(meditated.getId(), user, TODAY, 3, null);

        assertThat(result.points().delta()).isEqualTo(15);
    }

    @Test
    void deletingALogReversesItsAward() {
        UUID user = TestUsers.create(users, settings);
        ActivityType meditated = activities.create(user, "Meditated", ActivityPolarity.POSITIVE, 5, "sparkles");
        var logged = activities.log(meditated.getId(), user, TODAY, 2, null);

        var reversal = activities.deleteLog(logged.log().getId(), user);

        assertThat(reversal.delta()).isEqualTo(-10);
        assertThat(reversal.newTotal()).isEqualTo(0);
    }

    @Test
    void loggingAnArchivedActivityIsRejected() {
        UUID user = TestUsers.create(users, settings);
        ActivityType meditated = activities.create(user, "Meditated", ActivityPolarity.POSITIVE, 5, "sparkles");
        activities.archive(meditated.getId(), user);

        assertThatThrownBy(() -> activities.log(meditated.getId(), user, TODAY, 1, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void oneUserCannotLogAnotherUsersActivity() {
        UUID owner = TestUsers.create(users, settings);
        UUID other = TestUsers.create(users, settings);
        ActivityType meditated = activities.create(owner, "Meditated", ActivityPolarity.POSITIVE, 5, "sparkles");

        assertThatThrownBy(() -> activities.log(meditated.getId(), other, TODAY, 1, null)).isInstanceOf(ApiException.class);
    }
}
