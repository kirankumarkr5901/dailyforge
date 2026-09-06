package com.dailyforge.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.dailyforge.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Issues our own access tokens.
 *
 * Short-lived and stateless, which means they cannot be revoked — that is the trade for
 * not hitting the database on every request. Revocation lives on the refresh token,
 * which is why the access TTL is 15 minutes rather than a day.
 */
@Service
public class AccessTokenService {

    public static final String ISSUER = "dailyforge";

    private final AuthProperties properties;
    private final Clock clock;
    private final MACSigner signer;

    public AccessTokenService(AuthProperties properties, Clock clock) throws JOSEException {
        this.properties = properties;
        this.clock = clock;
        this.signer = new MACSigner(secretKey(properties.jwtSecret()));
    }

    /**
     * The key is validated at construction rather than at first login, so a short or
     * missing secret fails the deployment instead of the user.
     */
    static SecretKeySpec secretKey(String secret) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "dailyforge.auth.jwt-secret must be at least 32 bytes. "
                            + "Set DAILYFORGE_AUTH_JWT_SECRET to a random value.");
        }
        return new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("name", user.getDisplayName())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(expiresAt))
                        .jwtID(UUID.randomUUID().toString())
                        .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the access token", e);
        }

        return new IssuedAccessToken(jwt.serialize(), expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record IssuedAccessToken(String token, Instant expiresAt, long expiresInSeconds) {}
}
