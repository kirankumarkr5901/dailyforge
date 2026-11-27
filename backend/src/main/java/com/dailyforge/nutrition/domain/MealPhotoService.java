package com.dailyforge.nutrition.domain;

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
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a meal photo with Google Gemini (free tier): which dishes are on the plate, about
 * how much of each, and the nutrition for that amount. These are estimates, so nothing is
 * logged here; the phone shows them for checking and the user adds what they agree with.
 * Only the photo and a fixed instruction are sent, never who the user is.
 */
@Service
public class MealPhotoService {

    private static final Logger log = LoggerFactory.getLogger(MealPhotoService.class);

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    /** The photos the phone sends are shrunk to about 1 MB or less; this leaves room. */
    public static final int MAX_PHOTO_BYTES = 4 * 1024 * 1024;
    /** Guards the free quota (shared by the whole project) from a runaway phone. */
    public static final int DAILY_LIMIT_PER_USER = 30;
    private static final int MAX_DISHES = 8;

    static final String INSTRUCTION = """
            You are a nutrition assistant. Look at this photo of a meal and list each distinct \
            dish or food you can see (at most 8). For each one, estimate the portion in the photo \
            in grams, and the nutrition for that portion: protein, calories (kcal), fat, \
            carbohydrates, fiber and sugar. Many meals will be Indian home food (dal, rice, roti, \
            biryani, dosa, idli, sambar, curries); use typical home-cooked recipes. Give short, \
            plain dish names such as "Chicken biryani" or "2 chapati". Round to whole numbers. \
            If the photo shows no food, return an empty list.""";

    /** One dish, for the portion in the photo. Whole numbers, already inside the app's limits. */
    public record Dish(String name, int portionG, int proteinG, int kcal, int fatG, int carbsG, int fiberG, int sugarG) {}

    private final GeminiProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, AtomicInteger> usedToday = new ConcurrentHashMap<>();

    public MealPhotoService(GeminiProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    public boolean enabled() {
        return props.enabled();
    }

    public List<Dish> analyze(UUID userId, String photoBase64) {
        if (!props.enabled()) {
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "Meal photos aren't set up on this server yet.");
        }
        byte[] photo = decodeJpeg(photoBase64);
        countAgainstDailyLimit(userId);
        JsonNode body;
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(ENDPOINT))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", props.apiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody(photo)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                throw failure(res.statusCode(), res.body());
            }
            body = json.readTree(res.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini call failed: {}", e.toString());
            throw unavailable();
        }
        return parse(body);
    }

    private static byte[] decodeJpeg(String base64) {
        byte[] photo;
        try {
            String data = base64 == null ? "" : base64.replaceFirst("^data:[^,]*,", "");
            photo = Base64.getDecoder().decode(data.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.outOfRange("photo", "That photo didn't come through. Try again.");
        }
        if (photo.length < 3 || (photo[0] & 0xFF) != 0xFF || (photo[1] & 0xFF) != 0xD8) {
            throw ApiException.outOfRange("photo", "The photo must be a JPEG.");
        }
        if (photo.length > MAX_PHOTO_BYTES) {
            throw ApiException.outOfRange("photo", "That photo is too large.");
        }
        return photo;
    }

    private void countAgainstDailyLimit(UUID userId) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        usedToday.keySet().removeIf(k -> !k.endsWith(today));
        int used = usedToday.computeIfAbsent(userId + "@" + today, k -> new AtomicInteger()).incrementAndGet();
        if (used > DAILY_LIMIT_PER_USER) {
            throw new ApiException(
                    ErrorCode.DAILY_CAP_REACHED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "That's " + DAILY_LIMIT_PER_USER + " meal photos today. Log the rest by hand or try tomorrow.");
        }
    }

    private String requestBody(byte[] photo) throws Exception {
        Map<String, Object> number = Map.of("type", "integer");
        Map<String, Object> dish = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "portionG", number,
                        "proteinG", number,
                        "kcal", number,
                        "fatG", number,
                        "carbsG", number,
                        "fiberG", number,
                        "sugarG", number),
                "required", List.of("name", "portionG", "proteinG", "kcal", "fatG", "carbsG", "fiberG", "sugarG"));
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("dishes", Map.of("type", "array", "maxItems", MAX_DISHES, "items", dish)),
                "required", List.of("dishes"));
        Map<String, Object> request = Map.of(
                "model", props.model(),
                "input", List.of(
                        Map.of("type", "text", "text", INSTRUCTION),
                        Map.of("type", "image", "mime_type", "image/jpeg", "data", Base64.getEncoder().encodeToString(photo))),
                "response_format", Map.of("type", "text", "mime_type", "application/json", "schema", schema));
        return json.writeValueAsString(request);
    }

    /** Gemini's answer to dishes. The JSON text is in output_text, or in the model_output steps. */
    List<Dish> parse(JsonNode interaction) {
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
        JsonNode answer;
        try {
            answer = json.readTree(text);
        } catch (Exception e) {
            log.warn("Gemini answered something that isn't the expected JSON ({} chars)", text.length());
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.BAD_GATEWAY, "Couldn't read the dishes from that photo. Try another one.");
        }
        List<Dish> dishes = new ArrayList<>();
        for (JsonNode d : answer.path("dishes")) {
            String name = d.path("name").asString("").strip();
            if (name.isEmpty()) {
                continue;
            }
            dishes.add(new Dish(
                    name.length() > 60 ? name.substring(0, 60).strip() : name,
                    clamp(d.path("portionG"), 5000),
                    clamp(d.path("proteinG"), ProteinService.MAX_FOOD_G),
                    clamp(d.path("kcal"), ProteinService.MAX_KCAL),
                    clamp(d.path("fatG"), ProteinService.MAX_FAT_G),
                    clamp(d.path("carbsG"), 500),
                    clamp(d.path("fiberG"), 100),
                    clamp(d.path("sugarG"), 300)));
            if (dishes.size() == MAX_DISHES) {
                break;
            }
        }
        return dishes;
    }

    private static int clamp(JsonNode value, int max) {
        long n = Math.round(value.asDouble(0));
        return (int) Math.max(0, Math.min(max, n));
    }

    private static ApiException failure(int status, String body) {
        // The error body is Google's, never the user's; its first part says what went wrong.
        log.warn("Gemini answered {}: {}", status, body == null ? "" : body.substring(0, Math.min(300, body.length())));
        if (status == 429) {
            return new ApiException(
                    ErrorCode.DAILY_CAP_REACHED,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The free meal-photo allowance is used up for now. Try again later, or log it by hand.");
        }
        if (status == 401 || status == 403) {
            return new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "The meal-photo key on the server isn't accepted. Check GEMINI_API_KEY.");
        }
        return unavailable();
    }

    private static ApiException unavailable() {
        return new ApiException(
                ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "Couldn't read the photo right now. Try again in a minute.");
    }
}
