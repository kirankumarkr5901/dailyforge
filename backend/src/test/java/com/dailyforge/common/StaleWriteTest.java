package com.dailyforge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.error.StaleWrite;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.reward.domain.Reward;
import com.dailyforge.reward.domain.RewardService;
import com.dailyforge.reward.domain.RewardTier;
import com.dailyforge.testsupport.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The multi-device rule: a phone and a desktop both hold a copy of the same row, and
 * the one that has been sitting on a stale copy for days must not be able to overwrite
 * what the other one changed in the meantime.
 */
@SpringBootTest
@ActiveProfiles("test")
class StaleWriteTest {

    @Autowired private RewardService rewards;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void everyEditBumpsTheVersionTheClientMustEchoBack() {
        UUID user = TestUsers.create(users, settings);
        Reward reward = rewards.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, null);

        long afterCreate = reward.getVersion();
        rewards.update(reward.getId(), user, "Bigger snack", 80, "cookie", RewardTier.MICRO, true, null);
        long afterEdit = rewards.requireOwned(reward.getId(), user).getVersion();

        assertThat(afterEdit).isGreaterThan(afterCreate);
    }

    /**
     * The scenario the owner described: the desktop was last opened days ago, the phone
     * has edited since, and the desktop then saves its old copy.
     */
    @Test
    void aDeviceHoldingAnOldCopyIsRefusedRatherThanAllowedToOverwrite() {
        UUID user = TestUsers.create(users, settings);
        Reward reward = rewards.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, null);

        // The desktop loaded the reward here, and still believes this is current.
        long versionTheDesktopHolds = reward.getVersion();

        // Meanwhile the phone edits it twice.
        rewards.update(reward.getId(), user, "Snack v2", 60, "cookie", RewardTier.MICRO, true, null);
        rewards.update(reward.getId(), user, "Snack v3", 70, "cookie", RewardTier.MICRO, true, null);

        long current = rewards.requireOwned(reward.getId(), user).getVersion();

        assertThatThrownBy(() -> StaleWrite.check(versionTheDesktopHolds, current, "That reward"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.STALE_WRITE))
                .hasMessageContaining("Reload");

        // And nothing was lost: the phone's edit still stands.
        assertThat(rewards.requireOwned(reward.getId(), user).getName()).isEqualTo("Snack v3");
    }

    @Test
    void aDeviceThatHasJustReadTheRowIsAllowedToWrite() {
        UUID user = TestUsers.create(users, settings);
        Reward reward = rewards.create(user, "Snack", 50, "cookie", RewardTier.MICRO, true, null);

        long fresh = rewards.requireOwned(reward.getId(), user).getVersion();

        StaleWrite.check(fresh, fresh, "That reward"); // does not throw
        rewards.update(reward.getId(), user, "Snack v2", 60, "cookie", RewardTier.MICRO, true, null);

        assertThat(rewards.requireOwned(reward.getId(), user).getName()).isEqualTo("Snack v2");
    }

    /** A caller with no prior read (a server job, an internal transition) is not blocked. */
    @Test
    void anAbsentVersionSkipsTheCheckEntirely() {
        StaleWrite.check(null, 42L, "Anything"); // does not throw
    }
}
