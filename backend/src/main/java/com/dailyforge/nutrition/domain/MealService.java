package com.dailyforge.nutrition.domain;

import com.dailyforge.common.error.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meal shortcuts: "My breakfast" = 2 eggs + milk + oats, logged with one tap. A meal keeps
 * its own copy of each item's values, so it never changes behind the user's back when a
 * food is edited or deleted. Logging a meal goes through {@link ProteinService#logMany},
 * which reconciles the day's points once for the whole meal.
 */
@Service
public class MealService {

    public static final int MAX_ITEMS = 20;

    private final JdbcTemplate jdbc;
    private final ProteinService protein;

    public MealService(JdbcTemplate jdbc, ProteinService protein) {
        this.jdbc = jdbc;
        this.protein = protein;
    }

    public record Item(String foodName, ProteinService.Amounts amounts) {}

    public record Meal(UUID id, String name, List<Item> items) {
        public double proteinG() {
            return ProteinService.round1(items.stream().mapToDouble(i -> i.amounts().proteinG()).sum());
        }

        public int kcal() {
            return items.stream().mapToInt(i -> i.amounts().kcal()).sum();
        }
    }

    @Transactional(readOnly = true)
    public List<Meal> list(UUID userId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        jdbc.query(
                "SELECT id, name FROM meal WHERE user_id = ? ORDER BY sort_order, created_at",
                rs -> {
                    names.put(rs.getObject(1, UUID.class), rs.getString(2));
                },
                userId);
        Map<UUID, List<Item>> items = new LinkedHashMap<>();
        names.keySet().forEach(id -> items.put(id, new ArrayList<>()));
        jdbc.query(
                "SELECT mi.meal_id, mi.food_name, mi.grams, mi.kcal, mi.fat_g, mi.carbs_g, mi.fiber_g, mi.sugar_g "
                        + "FROM meal_item mi JOIN meal m ON m.id = mi.meal_id WHERE m.user_id = ? ORDER BY mi.sort_order",
                rs -> {
                    items.get(rs.getObject(1, UUID.class)).add(new Item(
                            rs.getString(2),
                            new ProteinService.Amounts(rs.getDouble(3), rs.getInt(4), rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8))));
                },
                userId);
        return names.entrySet().stream().map(e -> new Meal(e.getKey(), e.getValue(), items.get(e.getKey()))).toList();
    }

    @Transactional
    public Meal create(UUID userId, String name, List<Item> items) {
        if (name == null || name.isBlank() || name.trim().length() > 60) {
            throw ApiException.outOfRange("name", "Give the meal a name of up to 60 characters.");
        }
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw ApiException.outOfRange("items", "A meal needs between 1 and " + MAX_ITEMS + " items.");
        }
        items.forEach(i -> ProteinService.requireAmounts(i.amounts()));
        UUID id = UUID.randomUUID();
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM meal WHERE user_id = ?", Integer.class, userId);
        jdbc.update(
                "INSERT INTO meal (id, user_id, name, sort_order, created_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, name.trim(), next, Timestamp.from(Instant.now()));
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            ProteinService.Amounts a = it.amounts();
            jdbc.update(
                    "INSERT INTO meal_item (id, meal_id, food_name, grams, kcal, fat_g, carbs_g, fiber_g, sugar_g, sort_order) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), id, it.foodName().trim(), a.proteinG(), a.kcal(), a.fatG(), a.carbsG(), a.fiberG(), a.sugarG(), i);
        }
        return require(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        require(userId, id);
        jdbc.update("DELETE FROM meal WHERE id = ? AND user_id = ?", id, userId);
    }

    /** Logs every item of the meal on {@code date}, as separate entries a user can undo one by one. */
    @Transactional
    public ProteinService.DayWrite log(UUID userId, UUID id, LocalDate date) {
        Meal meal = require(userId, id);
        return protein.logMany(userId, date, meal.items().stream().map(i -> Map.entry(i.foodName(), i.amounts())).toList());
    }

    private Meal require(UUID userId, UUID id) {
        return list(userId).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("That meal"));
    }
}
