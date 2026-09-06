package com.dailyforge.identity.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.security.AccessTokenService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Signup, sign-in and session lifecycle.
 *
 * One rule shapes most of this class: <b>a person has one account.</b> Signing in with
 * Google using an address that already has a password account links the two rather than
 * creating a second, because a split account silently halves someone's history and there
 * is no good way to merge it afterwards.
 */
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenService accessTokens;
    private final GoogleTokenVerifier googleVerifier;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(
            UserRepository users,
            UserSettingsRepository settings,
            RefreshTokenService refreshTokens,
            AccessTokenService accessTokens,
            GoogleTokenVerifier googleVerifier,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.settings = settings;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.googleVerifier = googleVerifier;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Session signup(String email, String rawPassword, String displayName, String timeZone, String device) {
        String normalised = User.normaliseEmail(email);

        if (users.existsByEmail(normalised)) {
            throw new ApiException(
                    ErrorCode.AUTH_EMAIL_TAKEN,
                    HttpStatus.CONFLICT,
                    "That email already has an account. Sign in instead.",
                    "email",
                    java.util.Map.of());
        }

        User user = User.withPassword(normalised, displayName.trim(), passwordEncoder.encode(rawPassword));
        users.save(user);
        settings.save(UserSettings.forUser(user.getId(), timeZone));

        log.info("Created account {} by password", user.getId());
        return startSession(user, device);
    }

    @Transactional
    public Session login(String email, String rawPassword, String device) {
        Optional<User> found = users.findByEmail(User.normaliseEmail(email));

        // The same answer whether the address is unknown or the password is wrong, so the
        // endpoint cannot be used to discover which addresses have accounts.
        User user = found.orElseThrow(this::invalidCredentials);

        if (!user.hasPassword() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireActive(user);

        return startSession(user, device);
    }

    /**
     * Exchanges a verified Google ID token for a DailyForge session, creating or linking
     * the account as needed.
     */
    @Transactional
    public Session loginWithGoogle(String idToken, String timeZone, String device) {
        GoogleTokenVerifier.GoogleIdentity identity = googleVerifier.verify(idToken);

        Optional<User> bySub = users.findByGoogleSub(identity.subject());
        if (bySub.isPresent()) {
            User user = bySub.get();
            requireActive(user);
            return startSession(user, device);
        }

        String email = User.normaliseEmail(identity.email());
        Optional<User> byEmail = users.findByEmail(email);

        if (byEmail.isPresent()) {
            // Same verified address, so the same person. Link rather than duplicate.
            User user = byEmail.get();
            requireActive(user);
            user.linkGoogle(identity.subject());
            users.save(user);
            log.info("Linked Google identity to existing account {}", user.getId());
            return startSession(user, device);
        }

        User user = User.withGoogle(email, identity.displayName(), identity.subject());
        users.save(user);
        settings.save(UserSettings.forUser(user.getId(), timeZone));
        log.info("Created account {} by Google", user.getId());
        return startSession(user, device);
    }

    @Transactional
    public Session refresh(String refreshToken, String device) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken, device);

        User user =
                users.findById(rotation.userId())
                        .orElseThrow(() -> ApiException.notFound("That account"));
        requireActive(user);

        AccessTokenService.IssuedAccessToken access = accessTokens.issue(user);
        return new Session(user, access, rotation.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        // Idempotent on purpose: signing out twice, or with a token the server has
        // already forgotten, is a success from the user's point of view.
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.revoke(refreshToken);
        }
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("That account"));
    }

    @Transactional(readOnly = true)
    public UserSettings requireSettings(UUID userId) {
        return settings.findById(userId).orElseThrow(() -> ApiException.notFound("Those settings"));
    }

    public boolean googleEnabled() {
        return googleVerifier.isEnabled();
    }

    private Session startSession(User user, String device) {
        AccessTokenService.IssuedAccessToken access = accessTokens.issue(user);
        String refresh = refreshTokens.issue(user.getId(), device);
        return new Session(user, access, refresh);
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(
                    ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "That account is not active.");
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                HttpStatus.UNAUTHORIZED,
                "That email and password do not match.");
    }

    public record Session(
            User user, AccessTokenService.IssuedAccessToken accessToken, String refreshToken) {}
}
