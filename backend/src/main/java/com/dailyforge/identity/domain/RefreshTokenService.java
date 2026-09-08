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
            // A token presented after revocation is either a bug or a stolen copy. Either
            // way, end every session for this user rather than guessing which.
            //
            // This must commit in its own transaction: the exception below rolls the
            // current one back, and a revocation that gets rolled back by the very
            // failure that triggered it leaves the stolen session alive.
            newTransaction.executeWithoutResult(status -> revokeAllOf(existing.getUserId()));
            throw new ApiException(
                    ErrorCode.AUTH_TOKEN_EXPIRED,
                    HttpStatus.UNAUTHORIZED,
                    "That session has ended. Sign in again.");
        }

        existing.revoke(now);
        repository.save(existing);

        String replacement = issue(existing.getUserId(), deviceLabel);
        return new Rotation(existing.getUserId(), replacement);
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
