package com.dailyforge.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.habit.domain.ScheduleDays;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** Reading the assistant's answer, and the confirmed actions it can take. No network. */
@SpringBootTest
@ActiveProfiles("test")
class AssistantServiceTest {

    @Autowired private AssistantService assistant;
    @Autowired private ProteinService protein;
    @Autowired private HabitService habits;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    private static AssistantService.Action action(AssistantService.ActionType type, String name, int amount) {
        return new AssistantService.Action(type, "", name, 6, 140, 4, 20, 2, 1, amount, 0, List.of(), 0, 0, "");
    }

    @Test
    void unknownActionTypesAreDroppedAndRepsAreKeptSensible() {
        var reply = AssistantService.parse(JsonMapper.builder().build().readTree("""
                {"answer":"Sure.","actions":[
                  {"type":"DELETE_EVERYTHING","summary":"x"},
                  {"type":"LOG_SETS","summary":"Bench 3 sets","name":"Bench","weightKg":50,"reps":[10,0,8,500]}]}
                """));
        assertThat(reply.actions()).hasSize(1);
        assertThat(reply.actions().get(0).reps()).containsExactly(10, 8, 100);
    }

    @Test
    void confirmedActionsGoThroughTheAppsOwnServices() {
        UUID user = TestUsers.create(users, settings);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        habits.create(user, "Read 10 pages", "book", 10, HabitType.NORMAL, 0, null, null, ScheduleDays.EVERY_DAY, today);

        assistant.act(user, action(AssistantService.ActionType.LOG_FOOD, "2 roti", 0));
        assistant.act(user, action(AssistantService.ActionType.ADD_WATER, "", 2500));
        assertThat(assistant.act(user, action(AssistantService.ActionType.MARK_HABIT, "reading", 0))).contains("Read 10 pages");

        var day = protein.day(user, protein.today(user));
        assertThat(day.logs()).extracting(l -> l.getFoodName()).contains("2 roti");
        assertThat(day.waterMl()).isEqualTo(2500);

        assertThatThrownBy(() -> assistant.act(user, action(AssistantService.ActionType.MARK_HABIT, "swimming", 0)))
                .isInstanceOf(ApiException.class);
    }
}
