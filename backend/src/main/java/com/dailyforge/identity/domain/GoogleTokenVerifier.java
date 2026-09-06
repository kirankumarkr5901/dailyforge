package com.dailyforge.identity.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.security.AuthProperties;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Verifies a Google ID token, server-side.
 *
 * This is the part that must not be skipped. The browser hands us a token claiming
 * "this is kiran@example.com"; anyone can post that string. What makes it trustworthy is
 * checking Google's RS256 signature against Google's published keys, and then checking
 * that the token was minted for *this* application and has not expired.
 *
 * A verified Google token is still not a session. It is exchanged for our own access and
 * refresh tokens, so the rest of the app has exactly one kind of credential to reason
 * about.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> VALID_ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final AuthProperties properties;

    /** Built lazily: with Google disabled there is no reason to reach out to Google. */
    private volatile JwtDecoder decoder;

    public GoogleTokenVerifier(AuthProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.googleEnabled();
    }

    public GoogleIdentity verify(String idToken) {
        if (!isEnabled()) {
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    HttpStatus.NOT_IMPLEMENTED,
                    "Google sign-in is not configured on this server.");
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            // The reason is logged, never returned: telling a caller which check failed
            // helps them craft a token that passes it.
            log.warn("Rejected a Google ID token: {}", e.getMessage());
            throw invalid();
        }

        if (!VALID_ISSUERS.contains(jwt.getClaimAsString("iss"))) {
            throw invalid();
        }

        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(properties.googleClientId())) {
            // A token minted for a different application is not a login for this one.
            throw invalid();
        }

        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            // Account linking is by email, so an unverified address would let someone
            // claim an account they do not own.
            throw new ApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    HttpStatus.UNAUTHORIZED,
                    "That Google account has no verified email address.");
        }

        String email = jwt.getClaimAsString("email");
        String subject = jwt.getSubject();
        if (email == null || subject == null) {
            throw invalid();
        }

        String name = jwt.getClaimAsString("name");
        return new GoogleIdentity(subject, email, name == null || name.isBlank() ? email : name);
    }

    private JwtDecoder decoder() {
        JwtDecoder local = this.decoder;
        if (local == null) {
            synchronized (this) {
                local = this.decoder;
                if (local == null) {
                    // Nimbus caches and refreshes the key set on its own.
                    local = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
                    this.decoder = local;
                }
            }
        }
        return local;
    }

    private ApiException invalid() {
        return new ApiException(
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED,
                "That Google sign-in could not be verified. Try again.");
    }

    public record GoogleIdentity(String subject, String email, String displayName) {}
}
