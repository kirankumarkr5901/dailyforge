package com.dailyforge.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth configuration. Every value comes from the environment in a deployed
 * environment — nothing here is a committed secret.
 *
 * @param jwtSecret          signing key for our own access tokens. Must be at least 32
 *                           bytes; the app refuses to start otherwise, because a short
 *                           HMAC key is a forgeable one.
 * @param accessTokenTtl     15 minutes per spec §4.1. Short, because it cannot be revoked.
 * @param refreshTokenTtl    30 days per spec §4.1. Revocable, because it is stored.
 * @param googleClientId     Google OAuth client id. Blank disables Google sign-in
 *                           entirely rather than half-enabling a broken button.
 * @param bcryptStrength     cost 12 per spec §10.
 */
@ConfigurationProperties(prefix = "dailyforge.auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String googleClientId,
        int bcryptStrength) {

    public AuthProperties {
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
        bcryptStrength = bcryptStrength <= 0 ? 12 : bcryptStrength;
        googleClientId = googleClientId == null ? "" : googleClientId.trim();
    }

    /**
     * Google sign-in is on only when a client id is configured. This is what lets the
     * feature ship dark: the button is hidden, the endpoint answers honestly, and
     * nothing else in the app has to know.
     */
    public boolean googleEnabled() {
        return !googleClientId.isBlank();
    }
}
