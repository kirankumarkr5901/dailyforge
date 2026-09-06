package com.dailyforge.habit;

import com.dailyforge.common.time.DayService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A clock the test controls, so multi-day streak scenarios run instantly and
 * deterministically rather than depending on the wall clock or sleeping. Every habit
 * test that needs to walk several days imports this rather than repeating the
 * boilerplate.
 */
@TestConfiguration
public class HabitClockTestConfig {

    public static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();

        public void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // Deliberately NOT @Primary: it must not be picked up by unqualified `Clock`
    // injection points elsewhere (access/refresh tokens, auth rate limiting) — only
    // DayService below wants this fake clock, and it gets it by exact MutableClock type,
    // not by Clock-type autowiring. See ClockConfig's @Primary for the full reasoning.
    @Bean
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public DayService dayService(MutableClock clock) {
        return new DayService(clock);
    }
}
