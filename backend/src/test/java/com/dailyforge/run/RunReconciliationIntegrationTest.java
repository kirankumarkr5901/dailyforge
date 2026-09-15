package com.dailyforge.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.run.domain.RunService;
import com.dailyforge.run.domain.RunType;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec §5.3 applied to a user's whole run history: every run's own distance points,
 * the highest milestone it crosses, and the first-ever bonus for whichever run was
 * first to reach that far — recomputed fresh whenever any run changes.
 */
@SpringBootTest
@Import(RunClockTestConfig.class)
@ActiveProfiles("test")
class RunReconciliationIntegrationTest {

    @Autowired private RunService runService;
    @Autowired private PointsEntryRepository entries;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private RunClockTestConfig.MutableClock clock;

    private static final LocalDate DAY1 = LocalDate.of(2026, 3, 2);

    private void setToday(LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
    }

    @Test
    void aShortRunBelow10kEarnsOnlyDistancePoints() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);

        runService.log(user, DAY1, 5000, 1500, RunType.TEMPO, null, null);

        assertThat(entries.sumAmountForUser(user)).isEqualTo(50); // 5000 / 100
    }

    @Test
    void aFirstEverTenKPlusRunEarnsTheMilestoneAndTheFirstEverBonus() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);

        var write = runService.log(user, DAY1, 12_000, 3600, null, null, null);

        assertThat(write.run().getType()).isEqualTo(RunType.LONG); // auto-assigned at 10 km
        // 120 (distance) + 50 (milestone) + 50 (first-ever) = 220
        assertThat(entries.sumAmountForUser(user)).isEqualTo(220);
    }

    @Test
    void aSecondTenKPlusRunEarnsTheMilestoneButNotTheFirstEverBonusAgain() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        runService.log(user, DAY1, 12_000, 3600, null, null, null);

        setToday(DAY1.plusDays(1));
        runService.log(user, DAY1.plusDays(1), 11_000, 3400, null, null, null);

        // Run 1: 120 + 50 + 50 = 220. Run 2: 110 (distance) + 50 (milestone, no first-ever) = 160.
        assertThat(entries.sumAmountForUser(user)).isEqualTo(220 + 160);
    }

    @Test
    void deletingTheFirstMilestoneRunPromotesTheNextEarliestOneRetroactively() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        var first = runService.log(user, DAY1, 12_000, 3600, null, null, null);

        setToday(DAY1.plusDays(1));
        runService.log(user, DAY1.plusDays(1), 11_000, 3400, null, null, null);

        runService.delete(user, first.run().getId());

        // Only the 11 km run remains, and it is now the earliest 10 km+ run —
        // it must be promoted to earn the first-ever bonus it did not originally get.
        List<com.dailyforge.points.domain.PointsEntry> active =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(user, "RUN_LOG");
        boolean hasFirstBonus = active.stream().anyMatch(e -> e.getRuleCode().equals("RUN_FIRST_MILESTONE"));
        assertThat(hasFirstBonus).isTrue();
        assertThat(entries.sumAmountForUser(user)).isEqualTo(110 + 50 + 50); // distance + milestone + first-ever
    }

    @Test
    void aRunUnderTenKCannotBeLoggedAsLong() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);

        assertThatThrownBy(() -> runService.log(user, DAY1, 5000, 1500, RunType.LONG, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aRunUnderTenKRequiresAnExplicitType() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);

        assertThatThrownBy(() -> runService.log(user, DAY1, 5000, 1500, null, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void editingARunsDistanceBelowAMilestoneReversesItsBonus() {
        setToday(DAY1);
        UUID user = TestUsers.create(users, settings);
        var run = runService.log(user, DAY1, 12_000, 3600, null, null, null);
        assertThat(entries.sumAmountForUser(user)).isEqualTo(220);

        runService.update(user, run.run().getId(), 8_000, 2400, RunType.TEMPO, null, null);

        assertThat(entries.sumAmountForUser(user)).isEqualTo(80); // 8000 / 100, no milestone at all now
    }

    @Test
    void oneUsersRunsNeverAffectAnotherUsersMilestoneHistory() {
        setToday(DAY1);
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);

        runService.log(userA, DAY1, 12_000, 3600, null, null, null);

        assertThat(entries.sumAmountForUser(userB)).isZero();

        runService.log(userB, DAY1, 20_000, 6000, null, null, null);
        // B's own first 10k+ run earns its own first-ever bonus independently of A's history.
        boolean bHasFirstBonus =
                entries.findAllByUserIdAndSourceTypeAndReversedFalseAndReversesIdIsNull(userB, "RUN_LOG").stream()
                        .anyMatch(e -> e.getRuleCode().equals("RUN_FIRST_MILESTONE"));
        assertThat(bHasFirstBonus).isTrue();
    }
}
