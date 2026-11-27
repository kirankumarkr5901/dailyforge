package com.dailyforge.nutrition.api;

import com.dailyforge.nutrition.domain.ProteinFood;
import com.dailyforge.nutrition.domain.ProteinLog;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProteinDtos {

    private ProteinDtos() {}

    /** {@code grams} is protein, kept under its original name so older clients still read it. */
    public record FoodResponse(
            UUID id, String name, int grams, int kcal, int fatG, int carbsG, int fiberG, int sugarG, String source) {
        public static FoodResponse of(ProteinFood food) {
            return new FoodResponse(
                    food.getId(), food.getName(), food.getGrams(), food.getKcal(), food.getFatG(),
                    food.getCarbsG(), food.getFiberG(), food.getSugarG(), food.getSource());
        }
    }

    public record LogResponse(UUID id, String foodName, int grams, int kcal, int fatG, int carbsG, int fiberG, int sugarG) {
        public static LogResponse of(ProteinLog log) {
            return new LogResponse(
                    log.getId(), log.getFoodName(), log.getGrams(), log.getKcal(), log.getFatG(),
                    log.getCarbsG(), log.getFiberG(), log.getSugarG());
        }
    }

    public record DayResponse(
            LocalDate date,
            int targetG,
            int totalG,
            int calorieBudget,
            int totalKcal,
            int fatTargetG,
            int totalFatG,
            int carbsTargetG,
            int totalCarbsG,
            int fiberTargetG,
            int totalFiberG,
            int sugarLimitG,
            int totalSugarG,
            int waterTargetMl,
            int waterMl,
            List<LogResponse> entries,
            List<FoodResponse> foods) {
        public static DayResponse of(ProteinService.Day day) {
            return new DayResponse(
                    day.date(),
                    day.targetG(),
                    day.totalG(),
                    day.calorieBudget(),
                    day.totalKcal(),
                    day.fatTargetG(),
                    day.totalFatG(),
                    day.carbsTargetG(),
                    day.totalCarbsG(),
                    day.fiberTargetG(),
                    day.totalFiberG(),
                    day.sugarLimitG(),
                    day.totalSugarG(),
                    day.waterTargetMl(),
                    day.waterMl(),
                    day.logs().stream().map(LogResponse::of).toList(),
                    day.foods().stream().map(FoodResponse::of).toList());
        }
    }

    /** A write returns the updated day plus the points envelope, like every other logging surface. */
    public record DayWriteResponse(DayResponse day, PointsEnvelope points) {
        public static DayWriteResponse of(ProteinService.DayWrite write) {
            return new DayWriteResponse(DayResponse.of(write.day()), PointsEnvelope.of(write.points()));
        }
    }

    /** {@code grams} is protein. Calories and fat default to 0 when left out. */
    public record LogRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 60) String foodName,
            Integer grams,
            Integer kcal,
            Integer fatG,
            Integer carbsG,
            Integer fiberG,
            Integer sugarG) {}

    /** Any of the three; null leaves that target as it is. */
    public record TargetRequest(
            Integer targetG,
            Integer calorieBudget,
            Integer fatTargetG,
            Integer carbsTargetG,
            Integer fiberTargetG,
            Integer sugarLimitG,
            Integer waterTargetMl) {}

    public record WaterRequest(@NotNull LocalDate date, @NotNull Integer deltaMl) {}

    /** {@code source} is optional: PHOTO or BARCODE when saved from a meal photo or a scan. */
    public record CreateFoodRequest(
            @NotBlank @Size(max = 60) String name,
            Integer grams,
            Integer kcal,
            Integer fatG,
            Integer carbsG,
            Integer fiberG,
            Integer sugarG,
            String source) {}

    public record UpdateFoodRequest(
            @Size(max = 60) String name,
            Integer grams,
            Integer kcal,
            Integer fatG,
            Integer carbsG,
            Integer fiberG,
            Integer sugarG) {}
}
