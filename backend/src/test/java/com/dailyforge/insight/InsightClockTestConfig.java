package com.dailyforge.insight;

import com.dailyforge.common.time.DayService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A clock the test controls. See {@code com.dailyforge.habit.HabitClockTestConfig} for
 * the reasoning behind not marking the {@code Clock} bean itself {@code @Primary}.
 */
@TestConfiguration
public class InsightClockTestConfig {

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
