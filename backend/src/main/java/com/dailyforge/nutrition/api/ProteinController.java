package com.dailyforge.nutrition.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.security.StructuralChange;
import com.dailyforge.nutrition.api.ProteinDtos.CreateFoodRequest;
import com.dailyforge.nutrition.api.ProteinDtos.DayResponse;
import com.dailyforge.nutrition.api.ProteinDtos.DayWriteResponse;
import com.dailyforge.nutrition.api.ProteinDtos.FoodResponse;
import com.dailyforge.nutrition.api.ProteinDtos.LogRequest;
import com.dailyforge.nutrition.api.ProteinDtos.TargetRequest;
import com.dailyforge.nutrition.api.ProteinDtos.UpdateFoodRequest;
import com.dailyforge.nutrition.domain.ProteinService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protein meter. Logging and the daily target always work; changing the food list
 * is a structural change, like editing activity types, so it needs "Enable edit".
 */
@RestController
@RequestMapping("/api/v1/protein")
public class ProteinController {

    private final ProteinService protein;
    private final CurrentUser currentUser;
    private final com.dailyforge.nutrition.domain.MealService meals;
    private final com.dailyforge.nutrition.domain.BarcodeLookupService barcodes;
    private final com.dailyforge.nutrition.domain.MealPhotoService mealPhotos;
    private final com.dailyforge.nutrition.domain.ProteinSuggestionService suggestions;

    public ProteinController(
            ProteinService protein,
            CurrentUser currentUser,
            com.dailyforge.nutrition.domain.MealService meals,
            com.dailyforge.nutrition.domain.BarcodeLookupService barcodes,
            com.dailyforge.nutrition.domain.MealPhotoService mealPhotos,
            com.dailyforge.nutrition.domain.ProteinSuggestionService suggestions) {
        this.protein = protein;
        this.currentUser = currentUser;
        this.meals = meals;
        this.barcodes = barcodes;
        this.mealPhotos = mealPhotos;
        this.suggestions = suggestions;
    }

    public record SuggestionsRequest(@jakarta.validation.constraints.NotNull LocalDate date) {}

    public record SuggestionsResponse(java.util.List<com.dailyforge.nutrition.domain.ProteinSuggestionService.Suggestion> suggestions) {}

    /** "Fill my day": one meal that closes today's protein, fiber and calorie gaps together. */
    @PostMapping("/fill-day")
    public com.dailyforge.nutrition.domain.ProteinSuggestionService.MealIdea fillDay(
            @Valid @RequestBody SuggestionsRequest request) {
        return suggestions.fillDay(currentUser.require(), request.date());
    }

    /** "What should I eat to hit protein?": two or three options that fit what's left today. */
    @PostMapping("/suggestions")
    public SuggestionsResponse suggestions(@Valid @RequestBody SuggestionsRequest request) {
        return new SuggestionsResponse(suggestions.suggest(currentUser.require(), request.date()));
    }

    // ------------------------------------------------------------------ meal photos

    public record MealPhotoStatus(boolean enabled) {}

    /** {@code photo} is a JPEG, base64 (a data: URL prefix is fine). */
    public record MealPhotoRequest(@jakarta.validation.constraints.NotBlank String photo) {}

    public record MealTextRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 400) String description) {}

    /** The same estimates, from what the user typed or said ("2 idli, sambar and coffee"). */
    @PostMapping("/meal-text")
    public MealPhotoResponse readMealText(@Valid @RequestBody MealTextRequest request) {
        return new MealPhotoResponse(mealPhotos.describe(currentUser.require(), request.description()));
    }

    public record MealPhotoResponse(java.util.List<com.dailyforge.nutrition.domain.MealPhotoService.Dish> dishes) {}

    /** Whether "Snap a meal" is set up on this server. */
    @GetMapping("/meal-photo")
    public MealPhotoStatus mealPhotoStatus() {
        currentUser.require();
        return new MealPhotoStatus(mealPhotos.enabled());
    }

    /** Estimates only: nothing is logged until the user adds the rows they agree with. */
    @PostMapping("/meal-photo")
    public MealPhotoResponse readMealPhoto(@Valid @RequestBody MealPhotoRequest request) {
        return new MealPhotoResponse(mealPhotos.analyze(currentUser.require(), request.photo()));
    }

    public record LogManyRequest(
            @jakarta.validation.constraints.NotNull LocalDate date,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 12)
                    java.util.List<@Valid MealItemDto> items) {}

    /** Several entries in one write (the dishes from a photo): points reconciled once. */
    @PostMapping("/logs/many")
    public DayWriteResponse logMany(@Valid @RequestBody LogManyRequest request) {
        var entries = request.items().stream()
                .map(i -> java.util.Map.entry(i.foodName(), new ProteinService.Amounts(
                        orZero(i.grams()), orZero(i.kcal()), orZero(i.fatG()), orZero(i.carbsG()), orZero(i.fiberG()), orZero(i.sugarG()))))
                .toList();
        return DayWriteResponse.of(protein.logMany(currentUser.require(), request.date(), entries));
    }

    /** A packaged food by barcode, from Open Food Facts via the server. 404 when unknown. */
    @GetMapping("/barcode/{code}")
    public com.dailyforge.nutrition.domain.BarcodeLookupService.Product barcode(@PathVariable String code) {
        currentUser.require();
        var product = barcodes.lookup(code);
        if (product == null) {
            throw com.dailyforge.common.error.ApiException.notFound("That barcode");
        }
        return product;
    }

    /** {@code date} is optional: omitted means "today" in the user's own time zone. */
    @GetMapping("/day")
    public DayResponse day(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return DayResponse.of(protein.day(userId, date != null ? date : protein.today(userId)));
    }

    @PostMapping("/logs")
    public DayWriteResponse log(@Valid @RequestBody LogRequest request) {
        return DayWriteResponse.of(protein.log(
                currentUser.require(),
                request.date(),
                request.foodName(),
                new ProteinService.Amounts(
                        orZero(request.grams()),
                        orZero(request.kcal()),
                        orZero(request.fatG()),
                        orZero(request.carbsG()),
                        orZero(request.fiberG()),
                        orZero(request.sugarG()))));
    }

    @DeleteMapping("/logs/{id}")
    public DayWriteResponse deleteLog(@PathVariable UUID id) {
        return DayWriteResponse.of(protein.deleteLog(currentUser.require(), id));
    }

    /** Water in steps: +250, +500, or -250 to undo a tap. */
    @PostMapping("/water")
    public DayWriteResponse addWater(@Valid @RequestBody ProteinDtos.WaterRequest request) {
        return DayWriteResponse.of(protein.addWater(currentUser.require(), request.date(), request.deltaMl()));
    }

    @PutMapping("/target")
    public DayWriteResponse setTarget(@Valid @RequestBody TargetRequest request) {
        return DayWriteResponse.of(
                protein.setTargets(
                        currentUser.require(),
                        request.targetG(),
                        request.calorieBudget(),
                        request.fatTargetG(),
                        request.carbsTargetG(),
                        request.fiberTargetG(),
                        request.sugarLimitG(),
                        request.waterTargetMl()));
    }

    /**
     * Adding a food always works (owner request: saving a dish from a photo or a scan
     * shouldn't need "Enable edit"). Changing or deleting one is still structural.
     */
    @PostMapping("/foods")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodResponse addFood(@Valid @RequestBody CreateFoodRequest request) {
        return FoodResponse.of(protein.addFood(
                currentUser.require(),
                request.name(),
                new ProteinService.Amounts(
                        orZero(request.grams()),
                        orZero(request.kcal()),
                        orZero(request.fatG()),
                        orZero(request.carbsG()),
                        orZero(request.fiberG()),
                        orZero(request.sugarG())),
                request.source()));
    }

    @PatchMapping("/foods/{id}")
    @StructuralChange
    public FoodResponse updateFood(@PathVariable UUID id, @Valid @RequestBody UpdateFoodRequest request) {
        return FoodResponse.of(
                protein.updateFood(
                        currentUser.require(),
                        id,
                        request.name(),
                        request.grams(),
                        request.kcal(),
                        request.fatG(),
                        request.carbsG(),
                        request.fiberG(),
                        request.sugarG()));
    }

    @DeleteMapping("/foods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StructuralChange
    public void deleteFood(@PathVariable UUID id) {
        protein.deleteFood(currentUser.require(), id);
    }

    // ------------------------------------------------------------------ meals

    public record MealItemDto(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 60) String foodName,
            Double grams,
            Integer kcal,
            Double fatG,
            Double carbsG,
            Double fiberG,
            Double sugarG) {}

    public record MealResponse(UUID id, String name, double proteinG, int kcal, java.util.List<MealItemDto> items) {
        static MealResponse of(com.dailyforge.nutrition.domain.MealService.Meal m) {
            return new MealResponse(m.id(), m.name(), m.proteinG(), m.kcal(), m.items().stream()
                    .map(i -> new MealItemDto(i.foodName(), i.amounts().proteinG(), i.amounts().kcal(), i.amounts().fatG(),
                            i.amounts().carbsG(), i.amounts().fiberG(), i.amounts().sugarG()))
                    .toList());
        }
    }

    public record CreateMealRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 60) String name,
            @jakarta.validation.constraints.NotNull java.util.List<@Valid MealItemDto> items) {}

    public record LogMealRequest(@jakarta.validation.constraints.NotNull LocalDate date) {}

    @GetMapping("/meals")
    public java.util.List<MealResponse> meals() {
        return meals.list(currentUser.require()).stream().map(MealResponse::of).toList();
    }

    /** Saving a meal is structure, like a food: it needs "Enable edit". */
    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public MealResponse createMeal(@Valid @RequestBody CreateMealRequest request) {
        var items = request.items().stream()
                .map(i -> new com.dailyforge.nutrition.domain.MealService.Item(i.foodName(), new ProteinService.Amounts(
                        orZero(i.grams()), orZero(i.kcal()), orZero(i.fatG()), orZero(i.carbsG()), orZero(i.fiberG()), orZero(i.sugarG()))))
                .toList();
        return MealResponse.of(meals.create(currentUser.require(), request.name(), items));
    }

    @DeleteMapping("/meals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StructuralChange
    public void deleteMeal(@PathVariable UUID id) {
        meals.delete(currentUser.require(), id);
    }

    /** Logging a meal is just logging: it always works. */
    @PostMapping("/meals/{id}/log")
    public DayWriteResponse logMeal(@PathVariable UUID id, @Valid @RequestBody LogMealRequest request) {
        return DayWriteResponse.of(meals.log(currentUser.require(), id, request.date()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }
}
