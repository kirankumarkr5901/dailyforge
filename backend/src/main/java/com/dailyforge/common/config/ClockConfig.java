package com.dailyforge.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock is a bean so that time is an injectable dependency rather than a static call.
 * Tests replace it with a fixed clock to exercise streaks and rollovers without waiting.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
