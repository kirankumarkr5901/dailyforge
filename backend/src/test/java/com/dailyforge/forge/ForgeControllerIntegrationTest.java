package com.dailyforge.forge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Forge 66 through the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForgeControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    private static final String ANSWERS =
            """
            {"mode":"NORMAL","answers":{
              "MORNINGS":["DRAINED"],"MOVEMENT":["RARELY"],"SCREENS":["OVER_3H"],"ATTENTION":["MINUTES"],
              "FUEL":["MIXED"],"STRESS":["BOTTLE"],"LEARNING":["THIS_MONTH"],"PEOPLE":["CLOSE"],
              "PRIORITY":["FOCUS","FITNESS"]}}
            """;

    @BeforeEach
    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private String accessTokenFor(String email) throws Exception {
        String body =
                mockMvc
                        .perform(
                                post("/api/v1/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"a-long-enough-password","displayName":"T","timeZone":"UTC"}
                                                """
                                                        .formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }

    @Test
    void everyForgeEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/forge")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/forge/questionnaire")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(post("/api/v1/forge/program").contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theQuestionnaireHidesItsScoringWeights() throws Exception {
        String access = accessTokenFor("quiz@example.com");
        mockMvc
                .perform(get("/api/v1/forge/questionnaire").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lengthDays").value(66))
                .andExpect(jsonPath("$.questions[0].options[0].label").exists())
                .andExpect(jsonPath("$.questions[0].options[0].weights").doesNotExist());
    }

    @Test
    void startCompleteAndReadBackThroughTheApi() throws Exception {
        String access = accessTokenFor("forger@example.com");

        mockMvc
                .perform(get("/api/v1/forge").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").doesNotExist())
                .andExpect(jsonPath("$.profile.level").value(1));

        String started =
                mockMvc
                        .perform(
                                post("/api/v1/forge/program")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ANSWERS))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.program.dayNumber").value(1))
                        .andExpect(jsonPath("$.program.focusAreas[0]").value("FOCUS"))
                        .andExpect(jsonPath("$.program.calendar.length()").value(66))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode quest = json.readTree(started).get("program").get("quests").get(0);
        String questId = quest.get("id").asString();
        int questPoints = quest.get("points").asInt();

        mockMvc
                .perform(post("/api/v1/forge/quests/" + questId + "/completion").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.delta").value(questPoints))
                .andExpect(jsonPath("$.forge.program.quests[0].done").value(true));

        mockMvc
                .perform(delete("/api/v1/forge/quests/" + questId + "/completion").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.delta").value(-questPoints));

        mockMvc
                .perform(
                        post("/api/v1/forge/program")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ANSWERS))
                .andExpect(status().isConflict());

        String other = accessTokenFor("other@example.com");
        mockMvc
                .perform(post("/api/v1/forge/quests/" + questId + "/completion").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void anIncompleteQuestionnaireIsRefused() throws Exception {
        String access = accessTokenFor("half@example.com");
        mockMvc
                .perform(
                        post("/api/v1/forge/program")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"NORMAL\",\"answers\":{\"MORNINGS\":[\"RESTED\"]}}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
