package com.dailyforge.identity.api;

import com.dailyforge.identity.domain.User;
import com.dailyforge.identity.domain.UserSettings;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Request and response shapes for identity.
 *
 * Validation messages are user-facing copy, not developer notes: they say what to do
 * next rather than naming the constraint that failed.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank(message = "Enter your email address.")
                    @Email(message = "That does not look like an email address.")
                    @Size(max = 320)
                    String email,
            @NotBlank(message = "Choose a password.")
                    @Size(min = 10, max = 200, message = "Use at least 10 characters.")
                    String password,
            @NotBlank(message = "Enter a name to be called by.") @Size(max = 80) String displayName,
            @Size(max = 64) String timeZone) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your email address.") @Size(max = 320) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {}

    public record GoogleLoginRequest(
            @NotBlank(message = "That Google sign-in did not complete. Try again.") String idToken,
            @Size(max = 64) String timeZone) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(String refreshToken) {}

    /**
     * The refresh token is returned in the body rather than a cookie because the same
     * API serves an Android build at M9, where cookies are a poor fit. It is stored by
     * the client behind one TokenStorage interface.
     */
    public record AuthResponse(
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            MeResponse user) {}

    public record MeResponse(
            UUID id,
            String email,
            String displayName,
            boolean hasPassword,
            boolean googleLinked,
            SettingsResponse settings) {

        public static MeResponse of(User user, UserSettings settings) {
            return new MeResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.hasPassword(),
                    user.getGoogleSub() != null,
                    SettingsResponse.of(settings));
        }
    }

    public record SettingsResponse(
            String timeZone,
            String unitSystem,
            String theme,
            String weekStart,
            int commitmentBonus,
            BigDecimal heightCm,
            LocalTime reminderTime,
            Instant onboardingCompletedAt,
            /** Sent back on edit as If-Match so a stale device cannot overwrite a newer one. */
            long version) {

        public static SettingsResponse of(UserSettings settings) {
            return new SettingsResponse(
                    settings.getTimeZone(),
                    settings.getUnitSystem(),
                    settings.getTheme(),
                    settings.getWeekStart(),
                    settings.getCommitmentBonus(),
                    settings.getHeightCm(),
                    settings.getReminderTime(),
                    settings.getOnboardingCompletedAt(),
                    settings.getVersion());
        }
    }

    /** Every field optional: a PATCH changes only what it names. */
    public record UpdateSettingsRequest(
            @Size(max = 64) String timeZone,
            String unitSystem,
            String theme,
            Integer commitmentBonus,
            BigDecimal heightCm,
            LocalTime reminderTime,
            Boolean onboardingCompleted) {}

    /** Tells the frontend which sign-in methods this deployment actually offers. */
    public record AuthCapabilities(boolean passwordEnabled, boolean googleEnabled) {}
}
