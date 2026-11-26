package com.dailyforge.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.nutrition.domain.NutritionSettings;
import com.dailyforge.nutrition.domain.ProteinFood;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import com.dailyforge.nutrition.repo.ProteinFoodRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** An account from before the Indian dishes gets them once, without duplicates or resurrecting deletions. */
@SpringBootTest
@ActiveProfiles("test")
class StarterFoodsUpgradeTest {

    @Autowired private ProteinService protein;
    @Autowired private NutritionSettingsRepository nutritionSettings;
    @Autowired private ProteinFoodRepository foods;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void anOlderListGetsOnlyTheNewDishesOnce() {
        UUID user = TestUsers.create(users, settings);
        // An account on starter list 1 that kept one food and already added its own idli.
        NutritionSettings s = NutritionSettings.defaultsFor(user);
        s.markFoodsSeeded();
        nutritionSettings.save(s);
        foods.save(ProteinFood.create(user, "Whey scoop", 24, 120, 2, 0));
        foods.save(ProteinFood.create(user, "1 Idli", 2, 45, 0, 1));

        var first = protein.day(user, LocalDate.now(ZoneOffset.UTC)).foods();
        var again = protein.day(user, LocalDate.now(ZoneOffset.UTC)).foods();

        assertThat(first).hasSize(2 + 18 - 1); // every new dish except the idli it had
        assertThat(again).hasSize(first.size());
        assertThat(first.stream().map(ProteinFood::getName)).contains("Chicken biryani 100 g", "1 egg omelette", "1 plain dosa");
        assertThat(first.stream().filter(f -> f.getName().equalsIgnoreCase("1 idli"))).hasSize(1);
        // The older starters it had deleted (eggs, chicken...) do not come back.
        assertThat(first.stream().map(ProteinFood::getName)).doesNotContain("2 eggs", "Chicken 100 g");
    }
}
