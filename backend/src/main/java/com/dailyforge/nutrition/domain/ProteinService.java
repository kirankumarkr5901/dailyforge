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
            new StarterFood("2 eggs", 12, 140, 10, 1, 0, 1),
            new StarterFood("Chicken 100 g", 31, 165, 4, 0, 0, 0),
            new StarterFood("Paneer 100 g", 18, 265, 21, 3, 0, 3),
            new StarterFood("Curd 200 g", 7, 120, 6, 9, 0, 9),
            new StarterFood("Greek yogurt 150 g", 15, 100, 3, 6, 0, 5),
            new StarterFood("Soya chunks 50 g", 26, 172, 0, 17, 7, 4),
            new StarterFood("Dal bowl", 9, 180, 5, 25, 6, 1),
            new StarterFood("Whey scoop", 24, 120, 2, 3, 0, 2),
            new StarterFood("Milk 250 ml", 8, 150, 8, 12, 0, 12),
            new StarterFood("Fish 100 g", 22, 130, 3, 0, 0, 0),
            new StarterFood("Chana 1 cup", 12, 270, 4, 45, 12, 8),
            new StarterFood("Tofu 100 g", 12, 145, 9, 3, 2, 1));

    /**
     * Version 2 of the starter list: common Indian dishes and servings. Typical values per
     * serving (protein g, kcal, fat g, carbs g, fiber g, sugar g); home recipes vary, so
     * every value is the user's to correct in Settings.
     */
    static final List<StarterFood> INDIAN_FOODS = List.of(
            new StarterFood("Chicken biryani 100 g", 8, 180, 7, 22, 1, 1),
            new StarterFood("Veg biryani 100 g", 4, 150, 5, 23, 2, 2),
            new StarterFood("Dal (raw) 50 g", 12, 175, 1, 30, 8, 1),
            new StarterFood("1 plain dosa", 3, 120, 4, 18, 1, 0),
            new StarterFood("1 masala dosa", 5, 250, 10, 35, 3, 2),
            new StarterFood("1 idli", 2, 40, 0, 8, 1, 0),
            new StarterFood("1 chapati / roti", 3, 105, 3, 16, 2, 0),
            new StarterFood("1 plain paratha", 5, 230, 9, 32, 3, 1),
            new StarterFood("Rice 1 cup (cooked)", 4, 205, 0, 45, 1, 0),
            new StarterFood("Sambar 1 bowl", 6, 130, 4, 18, 5, 3),
            new StarterFood("Rajma 1 bowl", 10, 210, 5, 30, 8, 2),
            new StarterFood("Chole 1 bowl", 10, 250, 9, 32, 9, 3),
            new StarterFood("1 egg omelette", 6, 95, 7, 1, 0, 0),
            new StarterFood("Egg bhurji (2 eggs)", 13, 200, 15, 3, 1, 1),
            new StarterFood("Poha 1 plate", 5, 250, 8, 40, 2, 2),
            new StarterFood("Upma 1 plate", 6, 250, 9, 36, 3, 2),
            new StarterFood("Chicken curry 1 bowl", 22, 250, 15, 6, 1, 2),
            new StarterFood("Paneer butter masala 1 bowl", 14, 350, 26, 12, 2, 5));

    /** The current starter list version: 1 = the first twelve foods, 2 = plus INDIAN_FOODS. */
    static final int STARTER_VERSION = 2;

    /** Typical values per serving; each user's copy is theirs to correct. */
    record StarterFood(String name, int grams, int kcal, int fatG, int carbsG, int fiberG, int sugarG) {}

    public static final int MAX_KCAL = 3000;
    public static final int MAX_FAT_G = 300;

    /** What one entry adds. At least one of protein, calories and fat must be above zero. */
    /** Grams keep one decimal; calories are whole. */
    public record Amounts(double proteinG, int kcal, double fatG, double carbsG, double fiberG, double sugarG) {
        public Amounts(double proteinG, int kcal, double fatG) {
            this(proteinG, kcal, fatG, 0, 0, 0);
        }
    }

    public record Day(
            LocalDate date,
            int targetG,
            double totalG,
            int calorieBudget,
            int totalKcal,
            int fatTargetG,
            double totalFatG,
            int carbsTargetG,
            double totalCarbsG,
            int fiberTargetG,
            double totalFiberG,
            int sugarLimitG,
            double totalSugarG,
            int waterTargetMl,
            int waterMl,
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ProteinService(
            NutritionSettingsRepository settingsRepo,
            ProteinFoodRepository foods,
            ProteinLogRepository logs,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity,
            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.settingsRepo = settingsRepo;
        this.foods = foods;
        this.logs = logs;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
        this.jdbc = jdbc;
    }

    /** The user's settings, created with the starter foods on first use. */
    @Transactional
    public NutritionSettings settings(UUID userId) {
        NutritionSettings settings = settingsRepo.findById(userId).orElseGet(() -> NutritionSettings.defaultsFor(userId));
        if (!settings.isFoodsSeeded()) {
            int i = 0;
            for (StarterFood f : STARTER_FOODS) {
                saveStarter(userId, f, i++);
            }
            for (StarterFood f : INDIAN_FOODS) {
                saveStarter(userId, f, i++);
            }
            settings.markFoodsSeeded();
            settings.setStarterVersion(STARTER_VERSION);
        } else if (settings.getStarterVersion() < STARTER_VERSION) {
            // Already has a list: add only the newer foods, once, skipping any name the user
            // already has. Foods they deleted from the older list never come back.
            var existing = foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId);
            java.util.Set<String> names = existing.stream()
                    .map(f -> f.getName().toLowerCase(java.util.Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            int next = existing.stream().mapToInt(ProteinFood::getSortOrder).max().orElse(-1) + 1;
            for (StarterFood f : INDIAN_FOODS) {
                if (!names.contains(f.name().toLowerCase(java.util.Locale.ROOT))) {
                    saveStarter(userId, f, next++);
                }
            }
            settings.setStarterVersion(STARTER_VERSION);
        }
        return settingsRepo.save(settings);
    }

    private void saveStarter(UUID userId, StarterFood f, int sortOrder) {
        ProteinFood food = ProteinFood.create(userId, f.name(), f.grams(), f.kcal(), f.fatG(), sortOrder);
        food.updateMore(f.carbsG(), f.fiberG(), f.sugarG());
        foods.save(food);
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
                round1(dayLogs.stream().mapToDouble(ProteinLog::getGrams).sum()),
                settings.getCalorieBudget(),
                dayLogs.stream().mapToInt(ProteinLog::getKcal).sum(),
                settings.getFatTargetG(),
                round1(dayLogs.stream().mapToDouble(ProteinLog::getFatG).sum()),
                settings.getCarbsTargetG(),
                round1(dayLogs.stream().mapToDouble(ProteinLog::getCarbsG).sum()),
                settings.getFiberTargetG(),
                round1(dayLogs.stream().mapToDouble(ProteinLog::getFiberG).sum()),
                settings.getSugarLimitG(),
                round1(dayLogs.stream().mapToDouble(ProteinLog::getSugarG).sum()),
                settings.getWaterTargetMl(),
                waterMl(userId, date),
                dayLogs,
                foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId));
    }

    @Transactional
    public DayWrite log(UUID userId, LocalDate date, String foodName, Amounts amounts) {
        requireEditableDate(userId, date);
        requireAmounts(amounts);
        logs.save(ProteinLog.create(
                userId,
                date,
                foodName.trim(),
                amounts.proteinG(),
                amounts.kcal(),
                amounts.fatG(),
                amounts.carbsG(),
                amounts.fiberG(),
                amounts.sugarG()));
        logs.flush();
        return new DayWrite(day(userId, date), reconcile(userId, date));
    }

    /** The day's water so far, in millilitres. */
    public int waterMl(UUID userId, LocalDate date) {
        List<Integer> ml = jdbc.queryForList(
                "SELECT ml FROM water_log WHERE user_id = ? AND occurred_on = ?", Integer.class, userId, date);
        return ml.isEmpty() ? 0 : ml.get(0);
    }

    /** Adds (or, negative, takes away) water for the day; never below 0 or above 10 L. */
    @Transactional
    public DayWrite addWater(UUID userId, LocalDate date, int deltaMl) {
        requireEditableDate(userId, date);
        if (deltaMl == 0 || Math.abs(deltaMl) > 2000) {
            throw ApiException.outOfRange("deltaMl", "Add or remove up to 2,000 ml at a time.");
        }
        settings(userId);
        int next = Math.max(0, Math.min(10_000, waterMl(userId, date) + deltaMl));
        java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
        if (jdbc.update("UPDATE water_log SET ml = ?, updated_at = ? WHERE user_id = ? AND occurred_on = ?", next, now, userId, date) == 0) {
            jdbc.update("INSERT INTO water_log (user_id, occurred_on, ml, updated_at) VALUES (?, ?, ?, ?)", userId, date, next, now);
        }
        PointsResult paid = points.reconcile(userId, new ReconcileScope.WaterDay(userId, date));
        return new DayWrite(day(userId, date), paid);
    }

    /** Several entries at once (a meal): one write, and the day's points reconciled once. */
    @Transactional
    public DayWrite logMany(UUID userId, LocalDate date, List<java.util.Map.Entry<String, Amounts>> entries) {
        requireEditableDate(userId, date);
        for (var e : entries) {
            requireAmounts(e.getValue());
            Amounts a = e.getValue();
            logs.save(ProteinLog.create(
                    userId, date, e.getKey().trim(), a.proteinG(), a.kcal(), a.fatG(), a.carbsG(), a.fiberG(), a.sugarG()));
        }
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
        return setTargets(userId, targetG, calorieBudget, fatTargetG, null, null, null);
    }

    @Transactional
    public DayWrite setTargets(
            UUID userId,
            Integer targetG,
            Integer calorieBudget,
            Integer fatTargetG,
            Integer carbsTargetG,
            Integer fiberTargetG,
            Integer sugarLimitG) {
        return setTargets(userId, targetG, calorieBudget, fatTargetG, carbsTargetG, fiberTargetG, sugarLimitG, null);
    }

    @Transactional
    public DayWrite setTargets(
            UUID userId,
            Integer targetG,
            Integer calorieBudget,
            Integer fatTargetG,
            Integer carbsTargetG,
            Integer fiberTargetG,
            Integer sugarLimitG,
            Integer waterTargetMl) {
        // Every range check lives here, in the one version that does the work.
        if (carbsTargetG != null && (carbsTargetG < 10 || carbsTargetG > 800)) {
            throw ApiException.outOfRange("carbsTargetG", "Pick a carbs target between 10 and 800 g.");
        }
        if (fiberTargetG != null && (fiberTargetG < 5 || fiberTargetG > 100)) {
            throw ApiException.outOfRange("fiberTargetG", "Pick a fiber target between 5 and 100 g.");
        }
        if (sugarLimitG != null && (sugarLimitG < 5 || sugarLimitG > 300)) {
            throw ApiException.outOfRange("sugarLimitG", "Pick a sugar limit between 5 and 300 g.");
        }
        if (waterTargetMl != null && (waterTargetMl < 500 || waterTargetMl > 8000)) {
            throw ApiException.outOfRange("waterTargetMl", "Pick a water target between 500 and 8,000 ml.");
        }
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
        if (carbsTargetG != null) settings.setCarbsTargetG(carbsTargetG);
        if (fiberTargetG != null) settings.setFiberTargetG(fiberTargetG);
        if (sugarLimitG != null) settings.setSugarLimitG(sugarLimitG);
        if (waterTargetMl != null) settings.setWaterTargetMl(waterTargetMl);
        settingsRepo.saveAndFlush(settings);
        LocalDate today = today(userId);
        return new DayWrite(day(userId, today), reconcile(userId, today));
    }

    @Transactional
    public ProteinFood addFood(UUID userId, String name, Amounts amounts) {
        return addFood(userId, name, amounts, ProteinFood.SOURCE_LIST);
    }

    /** {@code source}: LIST, or PHOTO / BARCODE for a food saved from a meal photo or a scan. */
    @Transactional
    public ProteinFood addFood(UUID userId, String name, Amounts amounts, String source) {
        String from = source == null || source.isBlank() ? ProteinFood.SOURCE_LIST : source.trim().toUpperCase();
        if (!ProteinFood.SOURCES.contains(from)) {
            throw ApiException.outOfRange("source", "A food comes from the list, a photo or a barcode.");
        }
        requireAmounts(amounts);
        settings(userId);
        int next = foods.findAllByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .mapToInt(ProteinFood::getSortOrder)
                .max()
                .orElse(-1) + 1;
        ProteinFood food = ProteinFood.create(userId, name.trim(), amounts.proteinG(), amounts.kcal(), amounts.fatG(), next);
        food.updateMore(amounts.carbsG(), amounts.fiberG(), amounts.sugarG());
        food.setSource(from);
        return foods.save(food);
    }

    @Transactional
    public ProteinFood updateFood(UUID userId, UUID foodId, String name, Double grams, Integer kcal, Double fatG) {
        return updateFood(userId, foodId, name, grams, kcal, fatG, null, null, null);
    }

    @Transactional
    public ProteinFood updateFood(
            UUID userId,
            UUID foodId,
            String name,
            Double grams,
            Integer kcal,
            Double fatG,
            Double carbsG,
            Double fiberG,
            Double sugarG) {
        ProteinFood food = requireFood(userId, foodId);
        Amounts next = new Amounts(
                grams != null ? grams : food.getGrams(),
                kcal != null ? kcal : food.getKcal(),
                fatG != null ? fatG : food.getFatG(),
                carbsG != null ? carbsG : food.getCarbsG(),
                fiberG != null ? fiberG : food.getFiberG(),
                sugarG != null ? sugarG : food.getSugarG());
        requireAmounts(next);
        food.update(
                name != null && !name.isBlank() ? name.trim() : food.getName(), next.proteinG(), next.kcal(), next.fatG());
        food.updateMore(next.carbsG(), next.fiberG(), next.sugarG());
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

    static void requireAmounts(Amounts a) {
        if (a.proteinG() < 0 || a.proteinG() > MAX_FOOD_G) {
            throw ApiException.outOfRange("grams", "Protein per serving must be between 0 and " + MAX_FOOD_G + " g.");
        }
        if (a.kcal() < 0 || a.kcal() > MAX_KCAL) {
            throw ApiException.outOfRange("kcal", "Calories per serving must be between 0 and " + MAX_KCAL + ".");
        }
        if (a.fatG() < 0 || a.fatG() > MAX_FAT_G) {
            throw ApiException.outOfRange("fatG", "Fat per serving must be between 0 and " + MAX_FAT_G + " g.");
        }
        if (a.carbsG() < 0 || a.carbsG() > 500 || a.fiberG() < 0 || a.fiberG() > 100 || a.sugarG() < 0 || a.sugarG() > 300) {
            throw ApiException.outOfRange("carbsG", "Carbs 0-500 g, fiber 0-100 g and sugar 0-300 g per serving.");
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

    /** A sum of one-decimal values, without floating-point crumbs (0.1 + 0.2 = 0.3). */
    public static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
