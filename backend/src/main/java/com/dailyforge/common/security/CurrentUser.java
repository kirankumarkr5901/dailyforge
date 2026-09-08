package com.dailyforge.common.security;

import com.dailyforge.common.error.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the signed-in user from the security context.
 *
 * Every controller goes through here rather than reading a header or a path variable, so
 * "whose data is this" has exactly one answer in the codebase — which is what makes the
 * per-user scoping rule (spec §10) checkable.
 */
@Component
public class CurrentUser {

    /** The signed-in user, or empty for an anonymous visitor. */
    public Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                return Optional.of(UUID.fromString(jwt.getSubject()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** The signed-in user, or a 401 in the standard contract shape. */
    public UUID require() {
        return id().orElseThrow(ApiException::authRequired);
    }

    public boolean isAuthenticated() {
        return id().isPresent();
    }
}
