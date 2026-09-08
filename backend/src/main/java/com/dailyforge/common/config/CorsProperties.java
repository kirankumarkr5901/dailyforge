package com.dailyforge.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allowed browser origins, from configuration. Never a wildcard in a deployed
 * environment (spec §10, and docs/DEPLOYMENT.md phase 2).
 */
@ConfigurationProperties(prefix = "dailyforge.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
