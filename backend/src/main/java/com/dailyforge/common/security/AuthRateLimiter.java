package com.dailyforge.common.security;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rate limit for the auth endpoints (spec §10).
 *
 * A fixed window per caller, held in memory. That is honest about its limits: it resets
 * on restart and does not coordinate across instances, so it slows down password
 * guessing rather than preventing a distributed attack. For a single-instance app on a
 * free tier that is the right amount of machinery; when there is more than one instance
 * this moves to a shared store, and the interface here does not change.
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_CALLERS = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String caller) {
        Instant now = clock.instant();

        // Unbounded growth here would be a denial-of-service in itself.
        if (windows.size() > MAX_TRACKED_CALLERS) {
            windows.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(WINDOW).isBefore(now));
        }

        Window window =
                windows.compute(
                        caller,
                        (key, existing) ->
                                existing == null || existing.startedAt.plus(WINDOW).isBefore(now)
                                        ? new Window(now)
                                        : existing);

        if (window.attempts.incrementAndGet() > MAX_ATTEMPTS) {
            throw new ApiException(
                    ErrorCode.OUT_OF_RANGE,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Wait a minute and try again.");
        }
    }

    /** Called after a success, so a legitimate user is not punished for a typo earlier. */
    public void clear(String caller) {
        windows.remove(caller);
    }

    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger attempts = new AtomicInteger();

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }
    }
}
