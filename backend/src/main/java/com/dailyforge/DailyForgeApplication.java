package com.dailyforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DailyForge.
 *
 * A modular monolith: each package under {@code com.dailyforge} is a module that talks to
 * its neighbours through published service interfaces only, never through their
 * repositories. Points are awarded exclusively by the points module.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DailyForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DailyForgeApplication.class, args);
    }
}
