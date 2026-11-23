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
            new StarterFood("2 eggs", 12),
            new StarterFood("Chicken 100 g", 31),
            new StarterFood("Paneer 100 g", 18),
            new StarterFood("Curd 200 g", 7),
            new StarterFood("Greek yogurt 150 g", 15),
            new StarterFood("Soya chunks 50 g", 26),
            new StarterFood("Dal bowl", 9),
            new StarterFood("Whey scoop", 24),
            new StarterFood("Milk 250 ml", 8),
            new StarterFood("Fish 100 g", 22),
            new StarterFood("Chana 1 cup", 12),
            new StarterFood("Tofu 100 g", 12));

    record StarterFood(String name, int grams) {}

    public record Day(LocalDate date, int targetG, int totalG, List<ProteinLog> logs, List<ProteinFood> foods) {}

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
                foods.save(ProteinFood.create(userId, f.name(), f.grams(), i));
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
        int total = dayLogs.stream().mapToInt(ProteinLog::getGrams).sum();
        return new Day(date, settings.getProteinTargetG(), total, dayLogs, foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId));
    }

    @Transactional
    public DayWrite log(UUID userId, LocalDate date, String foodName, int grams) {
        requireEditableDate(userId, date);
        requireGrams(grams);
        logs.save(ProteinLog.create(userId, date, foodName.trim(), grams));
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

    /** A new target can move today across it, so today is reconciled straight away. */
    @Transactional
    public DayWrite setTarget(UUID userId, int targetG) {
        if (targetG < 1 || targetG > MAX_TARGET_G) {
            throw ApiException.outOfRange("targetG", "Pick a target between 1 and " + MAX_TARGET_G + " g.");
        }
        NutritionSettings settings = settings(userId);
        settings.setProteinTargetG(targetG);
        settingsRepo.saveAndFlush(settings);
        LocalDate today = today(userId);
        return new DayWrite(day(userId, today), reconcile(userId, today));
    }

    @Transactional
    public ProteinFood addFood(UUID userId, String name, int grams) {
        requireGrams(grams);
        settings(userId);
        int next = foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .mapToInt(ProteinFood::getSortOrder)
                .max()
                .orElse(-1) + 1;
        return foods.save(ProteinFood.create(userId, name.trim(), grams, next));
    }

    @Transactional
    public ProteinFood updateFood(UUID userId, UUID foodId, String name, Integer grams) {
        ProteinFood food = requireFood(userId, foodId);
        int newGrams = grams != null ? grams : food.getGrams();
        requireGrams(newGrams);
        food.update(name != null && !name.isBlank() ? name.trim() : food.getName(), newGrams);
        return foods.save(food);
    }

    @Transactional
    public void deleteFood(UUID userId, UUID foodId) {
        foods.delete(requireFood(userId, foodId));
    }

    private PointsResult reconcile(UUID userId, LocalDate date) {
        return points.reconcile(userId, new ReconcileScope.ProteinDay(userId, date));
    }

    private ProteinFood requireFood(UUID userId, UUID foodId) {
        return foods.findByIdAndUserId(foodId, userId).orElseThrow(() -> ApiException.notFound("That food"));
    }

    private static void requireGrams(int grams) {
        if (grams < 1 || grams > MAX_FOOD_G) {
            throw ApiException.outOfRange("grams", "Protein per serving must be between 1 and " + MAX_FOOD_G + " g.");
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
