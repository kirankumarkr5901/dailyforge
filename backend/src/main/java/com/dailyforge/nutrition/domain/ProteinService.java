package com.dailyforge.nutrition.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import com.dailyforge.nutrition.repo.ProteinFoodRepository;
import com.dailyforge.nutrition.repo.ProteinLogRepository;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The protein meter: tap what you eat, see the day's total against the target. The
 * day that reaches the target earns {@code PROTEIN_TARGET} points once; an undo that
 * drops it back below takes them back. Both go through reconciliation, so the ledger
 * always matches the day's logs.
 */
@Service
public class ProteinService {

    public static final int MAX_TARGET_G = 500;
    public static final int MAX_FOOD_G = 200;

    /** The starter list, from the Cut Block plan. Each user gets an editable copy. */
    static final List<StarterFood> STARTER_FOODS = List.of(
            new StarterFood("2 eggs", 12, 140, 10),
            new StarterFood("Chicken 100 g", 31, 165, 4),
            new StarterFood("Paneer 100 g", 18, 265, 21),
            new StarterFood("Curd 200 g", 7, 120, 6),
            new StarterFood("Greek yogurt 150 g", 15, 100, 3),
            new StarterFood("Soya chunks 50 g", 26, 172, 0),
            new StarterFood("Dal bowl", 9, 180, 5),
            new StarterFood("Whey scoop", 24, 120, 2),
            new StarterFood("Milk 250 ml", 8, 150, 8),
            new StarterFood("Fish 100 g", 22, 130, 3),
            new StarterFood("Chana 1 cup", 12, 270, 4),
            new StarterFood("Tofu 100 g", 12, 145, 9));

    /** Typical values per serving; each user's copy is theirs to correct. */
    record StarterFood(String name, int grams, int kcal, int fatG) {}

    public static final int MAX_KCAL = 3000;
    public static final int MAX_FAT_G = 300;

    /** What one entry adds: protein, calories, fat. At least one must be above zero. */
    public record Amounts(int proteinG, int kcal, int fatG) {}

    public record Day(
            LocalDate date,
            int targetG,
            int totalG,
            int calorieBudget,
            int totalKcal,
            int fatTargetG,
            int totalFatG,
            List<ProteinLog> logs,
            List<ProteinFood> foods) {}

    public record DayWrite(Day day, PointsResult points) {}

    private final NutritionSettingsRepository settingsRepo;
    private final ProteinFoodRepository foods;
    private final ProteinLogRepository logs;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public ProteinService(
            NutritionSettingsRepository settingsRepo,
            ProteinFoodRepository foods,
            ProteinLogRepository logs,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.settingsRepo = settingsRepo;
        this.foods = foods;
        this.logs = logs;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** The user's settings, created with the starter foods on first use. */
    @Transactional
    public NutritionSettings settings(UUID userId) {
        NutritionSettings settings = settingsRepo.findById(userId).orElseGet(() -> NutritionSettings.defaultsFor(userId));
        if (!settings.isFoodsSeeded()) {
            for (int i = 0; i < STARTER_FOODS.size(); i++) {
                StarterFood f = STARTER_FOODS.get(i);
                foods.save(ProteinFood.create(userId, f.name(), f.grams(), f.kcal(), f.fatG(), i));
            }
            settings.markFoodsSeeded();
        }
        return settingsRepo.save(settings);
    }

    public LocalDate today(UUID userId) {
        return dayService.today(zoneOf(userId));
    }

    @Transactional
    public Day day(UUID userId, LocalDate date) {
        NutritionSettings settings = settings(userId);
        List<ProteinLog> dayLogs = logs.findAllByUserIdAndOccurredOnOrderByCreatedAtAsc(userId, date);
        return new Day(
                date,
                settings.getProteinTargetG(),
                dayLogs.stream().mapToInt(ProteinLog::getGrams).sum(),
                settings.getCalorieBudget(),
                dayLogs.stream().mapToInt(ProteinLog::getKcal).sum(),
                settings.getFatTargetG(),
                dayLogs.stream().mapToInt(ProteinLog::getFatG).sum(),
                dayLogs,
                foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId));
    }

    @Transactional
    public DayWrite log(UUID userId, LocalDate date, String foodName, Amounts amounts) {
        requireEditableDate(userId, date);
        requireAmounts(amounts);
        logs.save(ProteinLog.create(userId, date, foodName.trim(), amounts.proteinG(), amounts.kcal(), amounts.fatG()));
        logs.flush();
        return new DayWrite(day(userId, date), reconcile(userId, date));
    }

    @Transactional
    public DayWrite deleteLog(UUID userId, UUID logId) {
        ProteinLog log = logs.findByIdAndUserId(logId, userId).orElseThrow(() -> ApiException.notFound("That entry"));
        requireEditableDate(userId, log.getOccurredOn());
        logs.delete(log);
        logs.flush();
        return new DayWrite(day(userId, log.getOccurredOn()), reconcile(userId, log.getOccurredOn()));
    }

    /**
     * Changes whichever targets are given (null leaves one as it is). A new protein
     * target can move today across it, so today is reconciled straight away. Past days'
     * calorie results stand as they were judged.
     */
    @Transactional
    public DayWrite setTargets(UUID userId, Integer targetG, Integer calorieBudget, Integer fatTargetG) {
        if (targetG != null && (targetG < 1 || targetG > MAX_TARGET_G)) {
            throw ApiException.outOfRange("targetG", "Pick a protein target between 1 and " + MAX_TARGET_G + " g.");
        }
        if (calorieBudget != null && (calorieBudget < 800 || calorieBudget > 6000)) {
            throw ApiException.outOfRange("calorieBudget", "Pick a calorie budget between 800 and 6000 kcal.");
        }
        if (fatTargetG != null && (fatTargetG < 10 || fatTargetG > 300)) {
            throw ApiException.outOfRange("fatTargetG", "Pick a fat target between 10 and 300 g.");
        }
        NutritionSettings settings = settings(userId);
        if (targetG != null) settings.setProteinTargetG(targetG);
        if (calorieBudget != null) settings.setCalorieBudget(calorieBudget);
        if (fatTargetG != null) settings.setFatTargetG(fatTargetG);
        settingsRepo.saveAndFlush(settings);
        LocalDate today = today(userId);
        return new DayWrite(day(userId, today), reconcile(userId, today));
    }

    @Transactional
    public ProteinFood addFood(UUID userId, String name, Amounts amounts) {
        requireAmounts(amounts);
        settings(userId);
        int next = foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .mapToInt(ProteinFood::getSortOrder)
                .max()
                .orElse(-1) + 1;
        return foods.save(ProteinFood.create(userId, name.trim(), amounts.proteinG(), amounts.kcal(), amounts.fatG(), next));
    }

    @Transactional
    public ProteinFood updateFood(UUID userId, UUID foodId, String name, Integer grams, Integer kcal, Integer fatG) {
        ProteinFood food = requireFood(userId, foodId);
        Amounts next = new Amounts(
                grams != null ? grams : food.getGrams(),
                kcal != null ? kcal : food.getKcal(),
                fatG != null ? fatG : food.getFatG());
        requireAmounts(next);
        food.update(
                name != null && !name.isBlank() ? name.trim() : food.getName(), next.proteinG(), next.kcal(), next.fatG());
        return foods.save(food);
    }

    @Transactional
    public void deleteFood(UUID userId, UUID foodId) {
        foods.delete(requireFood(userId, foodId));
    }

    /** Protein for the day, and the calorie budget too (which only pays once the day is over). */
    private PointsResult reconcile(UUID userId, LocalDate date) {
        PointsResult protein = points.reconcile(userId, new ReconcileScope.ProteinDay(userId, date));
        PointsResult calories = points.reconcile(userId, new ReconcileScope.CalorieDay(userId, date));
        return PointsResult.combine(List.of(protein, calories));
    }

    private ProteinFood requireFood(UUID userId, UUID foodId) {
        return foods.findByIdAndUserId(foodId, userId).orElseThrow(() -> ApiException.notFound("That food"));
    }

    private static void requireAmounts(Amounts a) {
        if (a.proteinG() < 0 || a.proteinG() > MAX_FOOD_G) {
            throw ApiException.outOfRange("grams", "Protein per serving must be between 0 and " + MAX_FOOD_G + " g.");
        }
        if (a.kcal() < 0 || a.kcal() > MAX_KCAL) {
            throw ApiException.outOfRange("kcal", "Calories per serving must be between 0 and " + MAX_KCAL + ".");
        }
        if (a.fatG() < 0 || a.fatG() > MAX_FAT_G) {
            throw ApiException.outOfRange("fatG", "Fat per serving must be between 0 and " + MAX_FAT_G + " g.");
        }
        if (a.proteinG() == 0 && a.kcal() == 0 && a.fatG() == 0) {
            throw ApiException.outOfRange("grams", "Enter at least one of protein, calories or fat.");
        }
    }

    private ZoneId zoneOf(UUID userId) {
        return dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
    }

    private void requireEditableDate(UUID userId, LocalDate date) {
        int generalDays = ruleConfigs.getSystemDefault("EDIT_WINDOW").getInt("generalDays");
        if (!dayService.isWithinEditWindow(date, zoneOf(userId), generalDays)) {
            throw new ApiException(ErrorCode.ENTRY_LOCKED, HttpStatus.FORBIDDEN, "This day can no longer be changed.");
        }
    }
}
