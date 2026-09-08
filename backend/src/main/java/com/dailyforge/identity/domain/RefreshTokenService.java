package com.dailyforge.identity.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.security.AuthProperties;
import com.dailyforge.identity.repo.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh tokens: opaque, random, stored hashed, and rotated on every use.
 *
 * Rotation is the point. Each refresh revokes the token presented and issues a new one,
 * so a stolen token is usable at most once, and the theft becomes visible the next time
 * the real client tries to use its now-revoked copy.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate newTransaction;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            AuthProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Returns the raw token, which is the only time it exists outside the client. */
    @Transactional
    public String issue(UUID userId, String deviceLabel) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        repository.save(RefreshToken.issue(userId, hash(raw), expiresAt, deviceLabel));
        return raw;
    }

    /**
     * Consumes a refresh token and issues its replacement.
     *
     * @return the user id the token belonged to, plus the new raw token
     */
    @Transactional
    public Rotation rotate(String rawToken, String deviceLabel) {
        RefreshToken existing =
                repository
                        .findByTokenHash(hash(rawToken))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ErrorCode.AUTH_TOKEN_EXPIRED,
                                                HttpStatus.UNAUTHORIZED,
                                                "That session has ended. Sign in again."));

        Instant now = clock.instant();
        if (!existing.isUsable(now)) {
            // A token presented after revocation is either one of this app's own contexts
            // losing a refresh race, or a stolen copy. Treating both as theft signed
            // people out of everything for merely opening the app in two places: an
            // installed PWA and a browser tab share localStorage but not the in-memory
            // "one refresh at a time" flag, so both can wake with the same expired access
            // token and both try to rotate. The loser did nothing wrong, and killing
            // every session — including the one just issued to the winner — is a far
            // worse outcome than the risk it was guarding against.
            //
            // The replacement chain tells them apart. A token whose successor was minted
            // moments ago and is still alive was rotated by this same user, just now:
            // that is a race, so refuse this one request and leave the winner's session
            // intact. Anything else — reused long after, or a successor already gone —
            // still looks like theft and still ends every session.
            if (isBenignRace(existing, now)) {
                throw new ApiException(
                        ErrorCode.AUTH_TOKEN_EXPIRED,
                        HttpStatus.UNAUTHORIZED,
                        "That token was already refreshed. Retry with the current one.");
            }

            // This must commit in its own transaction: the exception below rolls the
            // current one back, and a revocation that gets rolled back by the very
            // failure that triggered it leaves the stolen session alive.
            newTransaction.executeWithoutResult(status -> revokeAllOf(existing.getUserId()));
            throw new ApiException(
                    ErrorCode.AUTH_TOKEN_EXPIRED,
                    HttpStatus.UNAUTHORIZED,
                    "That session has ended. Sign in again.");
        }

        String replacement = issue(existing.getUserId(), deviceLabel);
        existing.rotateTo(hash(replacement), now);
        repository.save(existing);

        return new Rotation(existing.getUserId(), replacement);
    }

    /**
     * How long after a rotation a second presentation of the old token is still read as
     * this app racing itself rather than as theft.
     *
     * Generous enough to cover a phone waking several requests at once against a cold
     * API, short enough that a token copied off a device and replayed later is still
     * caught. A thief who replays within seconds of the real client, and whose replay
     * loses the race, gains nothing: the request is refused either way. What the window
     * changes is only whether the legitimate user's other sessions survive.
     */
    private static final Duration RACE_GRACE = Duration.ofMinutes(2);

    private boolean isBenignRace(RefreshToken presented, Instant now) {
        String successorHash = presented.getReplacedByHash();
        if (successorHash == null || presented.getRevokedAt() == null) {
            return false; // revoked by sign-out or by a previous theft response
        }
        if (presented.getRevokedAt().plus(RACE_GRACE).isBefore(now)) {
            return false; // too long ago to be the same wake-up
        }
        // The successor must still be alive. If it too has been revoked, someone has been
        // walking the chain and this is no longer an innocent duplicate.
        return repository.findByTokenHash(successorHash).map(t -> t.isUsable(now)).orElse(false);
    }

    @Transactional
    public void revoke(String rawToken) {
        Optional<RefreshToken> token = repository.findByTokenHash(hash(rawToken));
        token.ifPresent(
                found -> {
                    found.revoke(clock.instant());
                    repository.save(found);
                });
    }

    @Transactional
    public void revokeAllFor(UUID userId) {
        revokeAllOf(userId);
    }

    private void revokeAllOf(UUID userId) {
        Instant now = clock.instant();
        repository
                .findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(
                        token -> {
                            token.revoke(now);
                            repository.save(token);
                        });
    }

    /**
     * SHA-256 rather than BCrypt: the token is already 256 bits of entropy from a
     * CSPRNG, so there is nothing to brute-force and a slow hash would only add latency
     * to every refresh. BCrypt is for passwords, which are guessable.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record Rotation(UUID userId, String refreshToken) {}
}
