package com.dailyforge.nutrition.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Gemini, for reading a meal photo. A blank key switches the feature off: the
 * Nutrition screen then hides "Snap a meal" instead of failing. The model can be changed
 * without a code change. See docs/meal-photos.md for getting a free key.
 */
@ConfigurationProperties(prefix = "dailyforge.gemini")
public record GeminiProperties(String apiKey, String model) {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }
}
