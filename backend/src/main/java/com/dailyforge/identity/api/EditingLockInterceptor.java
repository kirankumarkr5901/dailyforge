package com.dailyforge.identity.api;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.security.StructuralChange;
import com.dailyforge.identity.domain.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enforces "Enable edit" on the server, not just by hiding buttons: the app is used from
 * more than one device, and a tab opened before the lock was switched on still has its
 * buttons. Every {@link StructuralChange} endpoint passes through here first.
 *
 * An anonymous request is let through untouched so the controller answers it with the
 * usual 401 — "sign in" is the more useful thing to be told than "editing is off".
 */
@Configuration
public class EditingLockInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final CurrentUser currentUser;
    private final IdentityService identity;

    public EditingLockInterceptor(CurrentUser currentUser, IdentityService identity) {
        this.currentUser = currentUser;
        this.identity = identity;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !method.hasMethodAnnotation(StructuralChange.class)) {
            return true;
        }
        UUID userId = currentUser.id().orElse(null);
        if (userId == null) {
            return true;
        }
        if (!identity.requireSettings(userId).isEditingEnabled()) {
            throw new ApiException(
                    ErrorCode.EDITING_LOCKED,
                    HttpStatus.FORBIDDEN,
                    "Editing is off. Turn on \"Enable edit\" in Settings to change this.");
        }
        return true;
    }
}
