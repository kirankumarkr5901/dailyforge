package com.dailyforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.domain.RefreshTokenService;
import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The refresh race, which signed a real user out of everything for using the app in two
 * places at once.
 *
 * An installed PWA and a browser tab share localStorage but not the in-memory "one
 * refresh at a time" flag, so both can wake with the same expired access token and both
 * try to rotate the same refresh token. The loser presented an already-rotated token and
 * was treated as a thief: every session revoked, including the one just issued to the
 * winner a moment earlier.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshRaceTest {

    @Autowired private RefreshTokenService tokens;
    @Autowired private RefreshTokenRepository repository;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void losingARefreshRaceRefusesThatRequestButKeepsTheSessionAlive() {
        UUID user = TestUsers.create(users, settings);
        String original = tokens.issue(user, "phone");

        // The winner rotates first and gets a working replacement.
        var winner = tokens.rotate(original, "phone");

        // The loser presents the same token moments later — the race, not a theft.
        assertThatThrownBy(() -> tokens.rotate(original, "phone"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already refreshed");

        // The whole point: the winner's session still works, so the user stays signed in.
        var afterRace = tokens.rotate(winner.refreshToken(), "phone");
        assertThat(afterRace.userId()).isEqualTo(user);
    }

    @Test
    void aTokenReusedWithNoLiveSuccessorStillEndsEverySession() {
        UUID user = TestUsers.create(users, settings);
        String original = tokens.issue(user, "phone");
        var rotated = tokens.rotate(original, "phone");

        // The successor is revoked, so the chain is broken — this is the theft shape,
        // not two contexts of one app waking together.
        tokens.revoke(rotated.refreshToken());

        assertThatThrownBy(() -> tokens.rotate(original, "phone"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Sign in again");

        assertThat(repository.findAllByUserIdAndRevokedAtIsNull(user)).isEmpty();
    }

    @Test
    void anUnknownTokenIsRejectedWithoutTouchingAnySession() {
        UUID user = TestUsers.create(users, settings);
        String good = tokens.issue(user, "phone");

        assertThatThrownBy(() -> tokens.rotate("not-a-real-token", "phone")).isInstanceOf(ApiException.class);

        // A garbage token must not be able to sign anyone out.
        assertThat(tokens.rotate(good, "phone").userId()).isEqualTo(user);
    }
}
