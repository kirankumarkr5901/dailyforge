package com.dailyforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.domain.UserSettings;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.nutrition.domain.CalorieRolloverParticipant;
import com.dailyforge.nutrition.domain.ProteinLog;
import com.dailyforge.nutrition.repo.ProteinLogRepository;
import com.dailyforge.points.repo.PointsEntryRepository;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Switching features off: needs "Enable edit", is remembered, and pauses automatic points. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeatureSwitchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private CalorieRolloverParticipant calorieRollover;
    @Autowired private ProteinLogRepository proteinLogs;
    @Autowired private PointsEntryRepository ledger;

    private String signUp() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"switch-" + UUID.randomUUID()
                                + "@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"T\",\"timeZone\":\"UTC\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }

    private org.springframework.test.web.servlet.ResultActions patchSettings(String token, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void switchingFeaturesNeedsEditingOnAndIsRemembered() throws Exception {
        String token = signUp();

        patchSettings(token, "{\"disabledFeatures\":[\"NUTRITION\"]}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));

        patchSettings(token, "{\"editingEnabled\":true,\"disabledFeatures\":[\"NUTRITION\",\"GOALS\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledFeatures[0]").value("GOALS"))
                .andExpect(jsonPath("$.disabledFeatures[1]").value("NUTRITION"));

        // Sending the same list again with editing off is not a change, so it is allowed.
        patchSettings(token, "{\"editingEnabled\":false}").andExpect(status().isOk());
        patchSettings(token, "{\"disabledFeatures\":[\"GOALS\",\"NUTRITION\"]}").andExpect(status().isOk());
    }

    @Test
    void anUnknownFeatureIsRefused() throws Exception {
        String token = signUp();
        patchSettings(token, "{\"editingEnabled\":true,\"disabledFeatures\":[\"HOME\"]}")
                .andExpect(status().is4xxClientError());
    }

    @Test
    void nutritionOffPausesTheCalorieBudgetPoints() {
        UUID user = TestUsers.create(users, settings);
        UserSettings s = settings.findById(user).orElseThrow();
        s.setDisabledFeatures(List.of("NUTRITION"));
        settings.save(s);
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        proteinLogs.save(ProteinLog.create(user, yesterday, "Lunch", 30, 700, 20));

        calorieRollover.onDayClosed(user, yesterday);

        assertThat(ledger.findAll().stream()
                        .filter(e -> e.getUserId().equals(user) && "CALORIE_BUDGET".equals(e.getRuleCode()))
                        .count())
                .isZero();
    }
}
