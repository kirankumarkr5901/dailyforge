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

    public record FoodResponse(UUID id, String name, int grams) {
        public static FoodResponse of(ProteinFood food) {
            return new FoodResponse(food.getId(), food.getName(), food.getGrams());
        }
    }

    public record LogResponse(UUID id, String foodName, int grams) {
        public static LogResponse of(ProteinLog log) {
            return new LogResponse(log.getId(), log.getFoodName(), log.getGrams());
        }
    }

    public record DayResponse(
            LocalDate date, int targetG, int totalG, List<LogResponse> entries, List<FoodResponse> foods) {
        public static DayResponse of(ProteinService.Day day) {
            return new DayResponse(
                    day.date(),
                    day.targetG(),
                    day.totalG(),
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

    public record LogRequest(@NotNull LocalDate date, @NotBlank @Size(max = 60) String foodName, @NotNull Integer grams) {}

    public record TargetRequest(@NotNull Integer targetG) {}

    public record CreateFoodRequest(@NotBlank @Size(max = 60) String name, @NotNull Integer grams) {}

    public record UpdateFoodRequest(@Size(max = 60) String name, Integer grams) {}
}
