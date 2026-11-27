package com.dailyforge.nutrition.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Looks a packaged food's barcode up in Open Food Facts, a free, open food database, from
 * the server rather than the phone. Phones were failing the direct call ("could not
 * reach") in ways that could not be reproduced; going through the API the app already
 * reaches removes CORS, security headers and blocking DNS from the picture, and lets the
 * request carry a client name as Open Food Facts asks of apps. Only the barcode is sent.
 */
@Service
public class BarcodeLookupService {

    private static final String USER_AGENT = "nutrition-tracker/1.0 (open-source fitness app)";

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public BarcodeLookupService(ObjectMapper json) {
        this.json = json;
    }

    /** Values per 100 g; {@code servingG} is the product's own serving size, when it has one. */
    public record Product(
            String code, String name, double proteinG, double kcal, double fatG, double carbsG, double fiberG, double sugarG, Double servingG) {}

    /** The product, or null when Open Food Facts doesn't know the barcode. */
    public Product lookup(String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\D", "");
        if (code.length() < 8 || code.length() > 14) {
            throw ApiException.outOfRange("code", "A barcode has 8 to 14 digits.");
        }
        JsonNode body;
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create("https://world.openfoodfacts.org/api/v2/product/" + code
                                    + ".json?fields=product_name,brands,nutriments,serving_quantity"))
                            .timeout(Duration.ofSeconds(12))
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                return null;
            }
            if (res.statusCode() >= 300) {
                throw unavailable();
            }
            body = json.readTree(res.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
        if (body.path("status").asInt(0) != 1 || !body.has("product")) {
            return null;
        }
        JsonNode p = body.get("product");
        JsonNode n = p.path("nutriments");
        String brand = p.path("brands").asString("").split(",")[0].trim();
        String name = (brand + " " + p.path("product_name").asString("").trim()).trim();
        double serving = p.path("serving_quantity").asDouble(0);
        return new Product(
                code,
                name.isEmpty() ? "Product " + code : name.length() > 50 ? name.substring(0, 50) : name,
                n.path("proteins_100g").asDouble(0),
                n.path("energy-kcal_100g").asDouble(0),
                n.path("fat_100g").asDouble(0),
                n.path("carbohydrates_100g").asDouble(0),
                n.path("fiber_100g").asDouble(0),
                n.path("sugars_100g").asDouble(0),
                serving > 0 ? serving : null);
    }

    private static ApiException unavailable() {
        return new ApiException(
                ErrorCode.CONFLICT, HttpStatus.SERVICE_UNAVAILABLE, "Open Food Facts isn't answering right now. Try again in a minute.");
    }
}
