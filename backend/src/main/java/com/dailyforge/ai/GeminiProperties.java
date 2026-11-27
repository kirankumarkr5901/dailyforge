package com.dailyforge.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Gemini, for reading a meal photo. A blank key switches the feature off: the
 * Nutrition screen then hides "Snap a meal" instead of failing. The model can be changed
 * without a code change. When it is overloaded, the fallback model is asked instead.
 * See docs/meal-photos.md for getting a free key.
 */
@ConfigurationProperties(prefix = "dailyforge.gemini")
public record GeminiProperties(String apiKey, String model, String fallbackModel) {

    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    public static final String DEFAULT_FALLBACK_MODEL = "gemini-3.8-flash";

    public GeminiProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        // Unset: whichever of the two defaults isn't the main model.
        fallbackModel = fallbackModel == null || fallbackModel.isBlank()
                ? (model.equals(DEFAULT_MODEL) ? DEFAULT_FALLBACK_MODEL : DEFAULT_MODEL)
                : fallbackModel.trim();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }
}
