package com.dailyforge.identity.api;

import com.dailyforge.common.security.AuthRateLimiter;
import com.dailyforge.identity.api.AuthDtos.AuthCapabilities;
import com.dailyforge.identity.api.AuthDtos.AuthResponse;
import com.dailyforge.identity.api.AuthDtos.GoogleLoginRequest;
import com.dailyforge.identity.api.AuthDtos.LoginRequest;
import com.dailyforge.identity.api.AuthDtos.LogoutRequest;
import com.dailyforge.identity.api.AuthDtos.MeResponse;
import com.dailyforge.identity.api.AuthDtos.RefreshRequest;
import com.dailyforge.identity.api.AuthDtos.SignupRequest;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.identity.domain.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IdentityService identity;
    private final AuthRateLimiter rateLimiter;

    public AuthController(IdentityService identity, AuthRateLimiter rateLimiter) {
        this.identity = identity;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Lets the sign-in sheet render only the methods this deployment supports, instead
     * of showing a Google button that cannot work.
     */
    @GetMapping("/capabilities")
    public AuthCapabilities capabilities() {
        return new AuthCapabilities(true, identity.googleEnabled());
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            @RequestHeader(value = "X-Device-Label", required = false) String device,
            HttpServletRequest http) {

        String caller = callerOf(http);
        rateLimiter.check(caller);

        IdentityService.Session session =
                identity.signup(
                        request.email(),
                        request.password(),
                        request.displayName(),
                        request.timeZone(),
                        device);

        rateLimiter.clear(caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Label", required = false) String device,
            HttpServletRequest http) {

        String caller = callerOf(http);
        rateLimiter.check(caller);

        IdentityService.Session session = identity.login(request.email(), request.password(), device);

        rateLimiter.clear(caller);
        return toResponse(session);
    }

    @PostMapping("/google")
    public AuthResponse google(
            @Valid @RequestBody GoogleLoginRequest request,
            @RequestHeader(value = "X-Device-Label", required = false) String device,
            HttpServletRequest http) {

        String caller = callerOf(http);
        rateLimiter.check(caller);

        IdentityService.Session session =
                identity.loginWithGoogle(request.idToken(), request.timeZone(), device);

        rateLimiter.clear(caller);
        return toResponse(session);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshRequest request,
            @RequestHeader(value = "X-Device-Label", required = false) String device) {
        return toResponse(identity.refresh(request.refreshToken(), device));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        identity.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse toResponse(IdentityService.Session session) {
        UserSettings settings = identity.requireSettings(session.user().getId());
        return new AuthResponse(
                session.accessToken().token(),
                session.accessToken().expiresInSeconds(),
                session.refreshToken(),
                MeResponse.of(session.user(), settings));
    }

    /**
     * Rate-limit key. Behind Render or Netlify the socket address is the proxy, so the
     * forwarded header is used when present — and only its first entry, because the rest
     * is attacker-controlled.
     */
    private String callerOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
