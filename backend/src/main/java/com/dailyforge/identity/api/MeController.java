package com.dailyforge.identity.api;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.identity.api.AuthDtos.MeResponse;
import com.dailyforge.identity.api.AuthDtos.SettingsResponse;
import com.dailyforge.identity.api.AuthDtos.UpdateSettingsRequest;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.identity.domain.UserSettings;
import com.dailyforge.identity.repo.UserSettingsRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private static final Set<String> UNIT_SYSTEMS = Set.of("METRIC", "IMPERIAL");
    private static final Set<String> THEMES = Set.of("SYSTEM", "LIGHT", "DARK");

    private final IdentityService identity;
    private final UserSettingsRepository settingsRepository;
    private final CurrentUser currentUser;

    public MeController(
            IdentityService identity,
            UserSettingsRepository settingsRepository,
            CurrentUser currentUser) {
        this.identity = identity;
        this.settingsRepository = settingsRepository;
        this.currentUser = currentUser;
    }

    /**
     * Reads are open across the API for anonymous browsing, but "me" is the one read
     * that cannot be anonymous — there is no such thing as an anonymous profile.
     */
    @GetMapping
    public MeResponse me() {
        UUID userId = currentUser.require();
        return MeResponse.of(identity.requireUser(userId), identity.requireSettings(userId));
    }

    @PatchMapping("/settings")
    @Transactional
    public SettingsResponse updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        UUID userId = currentUser.require();
        UserSettings settings = identity.requireSettings(userId);

        if (request.timeZone() != null) {
            // Every streak and heatmap cell in the app resolves through this value, so a
            // bad zone is not a cosmetic error.
            if (!ZoneId.getAvailableZoneIds().contains(request.timeZone())) {
                throw ApiException.outOfRange("timeZone", "That is not a known time zone.");
            }
            settings.setTimeZone(request.timeZone());
        }

        if (request.unitSystem() != null) {
            requireOneOf(request.unitSystem(), UNIT_SYSTEMS, "unitSystem", "That unit system is not supported.");
            settings.setUnitSystem(request.unitSystem());
        }

        if (request.theme() != null) {
            requireOneOf(request.theme(), THEMES, "theme", "That theme is not supported.");
            settings.setTheme(request.theme());
        }

        if (request.commitmentBonus() != null) {
            if (request.commitmentBonus() < 0 || request.commitmentBonus() > 1000) {
                throw ApiException.outOfRange("commitmentBonus", "That looks out of range. Use 0 to 1000.");
            }
            settings.setCommitmentBonus(request.commitmentBonus());
        }

        if (request.heightCm() != null) {
            double height = request.heightCm().doubleValue();
            if (height < 50 || height > 260) {
                throw ApiException.outOfRange("heightCm", "That looks out of range. Check the height.");
            }
            settings.setHeightCm(request.heightCm());
        }

        if (request.reminderTime() != null) {
            settings.setReminderTime(request.reminderTime());
        }

        if (Boolean.TRUE.equals(request.onboardingCompleted())
                && settings.getOnboardingCompletedAt() == null) {
            settings.setOnboardingCompletedAt(Instant.now());
        }

        settingsRepository.save(settings);
        return SettingsResponse.of(settings);
    }

    private void requireOneOf(String value, Set<String> allowed, String field, String message) {
        if (!allowed.contains(value)) {
            throw ApiException.outOfRange(field, message);
        }
    }
}
