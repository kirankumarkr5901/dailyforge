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

    public ProteinController(ProteinService protein, CurrentUser currentUser) {
        this.protein = protein;
        this.currentUser = currentUser;
    }

    /** {@code date} is optional: omitted means "today" in the user's own time zone. */
    @GetMapping("/day")
    public DayResponse day(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return DayResponse.of(protein.day(userId, date != null ? date : protein.today(userId)));
    }

    @PostMapping("/logs")
    public DayWriteResponse log(@Valid @RequestBody LogRequest request) {
        return DayWriteResponse.of(protein.log(currentUser.require(), request.date(), request.foodName(), request.grams()));
    }

    @DeleteMapping("/logs/{id}")
    public DayWriteResponse deleteLog(@PathVariable UUID id) {
        return DayWriteResponse.of(protein.deleteLog(currentUser.require(), id));
    }

    @PutMapping("/target")
    public DayWriteResponse setTarget(@Valid @RequestBody TargetRequest request) {
        return DayWriteResponse.of(protein.setTarget(currentUser.require(), request.targetG()));
    }

    @PostMapping("/foods")
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public FoodResponse addFood(@Valid @RequestBody CreateFoodRequest request) {
        return FoodResponse.of(protein.addFood(currentUser.require(), request.name(), request.grams()));
    }

    @PatchMapping("/foods/{id}")
    @StructuralChange
    public FoodResponse updateFood(@PathVariable UUID id, @Valid @RequestBody UpdateFoodRequest request) {
        return FoodResponse.of(protein.updateFood(currentUser.require(), id, request.name(), request.grams()));
    }

    @DeleteMapping("/foods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StructuralChange
    public void deleteFood(@PathVariable UUID id) {
        protein.deleteFood(currentUser.require(), id);
    }
}
