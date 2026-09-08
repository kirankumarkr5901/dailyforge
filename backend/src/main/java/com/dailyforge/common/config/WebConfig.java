package com.dailyforge.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (cors.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                // Idempotency-Key is sent on every write (spec §4.5); If-Match carries
                // the version an edit was made against (see StaleWrite).
                //
                // Both must be listed or the browser's preflight strips them and the
                // request fails before it arrives. This is invisible in development,
                // where the dev server proxies /api and there is no cross-origin
                // request to preflight at all — it only appears once the frontend and
                // the API are on different hosts, which is to say, in production.
                .allowedHeaders("Authorization", "Content-Type", "Idempotency-Key", "If-Match")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
