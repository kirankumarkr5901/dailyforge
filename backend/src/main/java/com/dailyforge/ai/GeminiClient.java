package com.dailyforge.ai;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google Gemini (free tier), shared by every AI feature: one prompt, optionally a JPEG,
 * and a JSON schema the answer must follow. Only what the feature puts in the prompt is
 * sent: never the user's name, email or id.
 *
 * <p>Gemini answers 503 when a model is overloaded, and free-tier calls are turned away
 * first. So each call tries the chosen model, once more after a short pause, then the
 * fallback model. Each user also has a daily allowance per feature, so one runaway phone
 * can't use up the project's shared free quota.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";

    private final GeminiProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, AtomicInteger> usedToday = new ConcurrentHashMap<>();

    public GeminiClient(GeminiProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** Off without a key: the feature says so instead of failing. */
    public void requireEnabled() {
        if (!props.enabled()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "AI features aren't set up on this server yet.");
        }
    }

    /**
     * Counts one use of {@code feature} for the user today and refuses past
     * {@code dailyLimit}, with {@code what} naming it in the message ("meal photos").
     */
    public void countUse(UUID userId, String feature, int dailyLimit, String what) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        usedToday.keySet().removeIf(k -> !k.endsWith(today));
        int used = usedToday.computeIfAbsent(userId + "|" + feature + "|" + today, k -> new AtomicInteger()).incrementAndGet();
        if (used > dailyLimit) {
            throw new ApiException(
                    ErrorCode.DAILY_CAP_REACHED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "That's " + dailyLimit + " " + what + " today. Try again tomorrow.");
        }
    }

    /** The answer as JSON, shaped by {@code schema}. {@code jpeg} may be null. */
    public JsonNode generate(String prompt, byte[] jpeg, Map<String, Object> schema) {
        requireEnabled();
        List<String> attempts = new ArrayList<>(List.of(props.model(), props.model()));
        if (!props.fallbackModel().equals(props.model())) {
            attempts.add(props.fallbackModel());
        }
        JsonNode interaction = null;
        for (int i = 0; i < attempts.size() && interaction == null; i++) {
            if (i > 0) {
                pause(i == 1 ? 1500 : 500);
            }
            interaction = call(attempts.get(i), prompt, jpeg, schema, i == attempts.size() - 1);
        }
        return answer(interaction);
    }

    /** The interaction, or null when the model is busy (or unreachable) and another try is left. */
    private JsonNode call(String model, String prompt, byte[] jpeg, Map<String, Object> schema, boolean lastTry) {
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(ENDPOINT))
                            .timeout(Duration.ofSeconds(45))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", props.apiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, prompt, jpeg, schema)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                if (!lastTry && retryable(res.statusCode())) {
                    log.info("Gemini {} answered {}; trying again", model, res.statusCode());
                    return null;
                }
                throw failure(res.statusCode(), res.body());
            }
            return json.readTree(res.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini call to {} failed: {}", model, e.toString());
            if (!lastTry) {
                return null;
            }
            throw unavailable();
        }
    }

    private String requestBody(String model, String prompt, byte[] jpeg, Map<String, Object> schema) throws Exception {
        List<Object> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", prompt));
        if (jpeg != null) {
            input.add(Map.of("type", "image", "mime_type", "image/jpeg", "data", Base64.getEncoder().encodeToString(jpeg)));
        }
        return json.writeValueAsString(Map.of(
                "model", model,
                "input", input,
                "response_format", Map.of("type", "text", "mime_type", "application/json", "schema", schema)));
    }

    /** The JSON the model wrote: in output_text, or in the model_output steps. */
    JsonNode answer(JsonNode interaction) {
        String text = interaction.path("output_text").asString("");
        if (text.isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode step : interaction.path("steps")) {
                if ("model_output".equals(step.path("type").asString(""))) {
                    for (JsonNode content : step.path("content")) {
                        if ("text".equals(content.path("type").asString(""))) {
                            sb.append(content.path("text").asString(""));
                        }
                    }
                }
            }
            text = sb.toString();
        }
        text = text.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            return json.readTree(text);
        } catch (Exception e) {
            log.warn("Gemini answered something that isn't the expected JSON ({} chars)", text.length());
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.BAD_GATEWAY, "The AI's answer didn't come through properly. Try again.");
        }
    }

    /** Overloaded or a passing server error: worth another try. */
    static boolean retryable(int status) {
        return status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ApiException failure(int status, String body) {
        // The error body is Google's, never the user's; its first part says what went wrong.
        log.warn("Gemini answered {}: {}", status, body == null ? "" : body.substring(0, Math.min(300, body.length())));
        if (status == 429) {
            return new ApiException(
                    ErrorCode.DAILY_CAP_REACHED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The free AI allowance is used up for now. Try again later.");
        }
        if (status == 401 || status == 403) {
            return new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "The AI key on the server isn't accepted. Check GEMINI_API_KEY.");
        }
        if (retryable(status)) {
            return new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "Gemini is busy right now. Try again in a minute.");
        }
        return unavailable();
    }

    private static ApiException unavailable() {
        return new ApiException(
                ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "Couldn't reach the AI right now. Try again in a minute.");
    }

    // ------------------------------------------------------------------ schema helpers

    public static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    public static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    public static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    public static Map<String, Object> array(Map<String, Object> items, int maxItems) {
        return Map.of("type", "array", "maxItems", maxItems, "items", items);
    }

    /** An object whose listed properties are all required. */
    public static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()));
    }
}
