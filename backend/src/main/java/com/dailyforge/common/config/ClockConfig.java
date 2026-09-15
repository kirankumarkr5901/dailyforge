package com.dailyforge.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * The clock is a bean so that time is an injectable dependency rather than a static call.
 * Tests that exercise multi-day streak/rollover logic replace {@link
 * com.dailyforge.common.time.DayService}'s clock with a fake, mutable one — but token
 * issuance and validation (spec's 15-minute access token TTL) must keep running against
 * real wall-clock time regardless, or a token minted against a clock parked in the past
 * reads as already-expired against real time on the very next request. Marking this bean
 * {@code @Primary} means every unqualified {@code Clock} injection point (access tokens,
 * refresh tokens, auth rate limiting) keeps resolving to real time even when a test
 * context also registers a fake {@code Clock} subtype for {@code DayService} alone.
 */
@Configuration
public class ClockConfig {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
