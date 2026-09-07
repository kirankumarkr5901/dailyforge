package com.dailyforge.run;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The run endpoints through the real filter chain (spec §10's cross-user test, applied to runs). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RunClockTestConfig.class)
@ActiveProfiles("test")
class RunControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private RunClockTestConfig.MutableClock clock;

    @BeforeEach
    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private void setToday(java.time.LocalDate date) {
        clock.set(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plusSeconds(3600 * 12));
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
    void everyRunEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/runs")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(post("/api/v1/runs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingALongRunThroughTheRealEndpointAutoAssignsTheTypeAndAwardsTheMilestone() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String access = accessTokenFor("runner@example.com");

        mockMvc
                .perform(
                        post("/api/v1/runs")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"date":"2026-03-12","distanceMeters":12000,"durationSeconds":3600}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.type").value("LONG"))
                // 120 (distance) + 50 (milestone) + 50 (first-ever) = 220
                .andExpect(jsonPath("$.points.delta").value(220));
    }

    @Test
    void aShortRunRequiresAnExplicitTypeChoice() throws Exception {
        String access = accessTokenFor("short@example.com");

        mockMvc
                .perform(
                        post("/api/v1/runs")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"date":"2026-03-12","distanceMeters":5000,"durationSeconds":1500}
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUT_OF_RANGE"));
    }

    @Test
    void deletingARunReversesItsPointsAndUserACannotDeleteUserBsRun() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String userA = accessTokenFor("a@example.com");
        String userB = accessTokenFor("b@example.com");

        String logBody =
                mockMvc
                        .perform(
                                post("/api/v1/runs")
                                        .header("Authorization", "Bearer " + userA)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"date":"2026-03-12","distanceMeters":5000,"durationSeconds":1500,"type":"TEMPO"}
                                                """))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String runId = json.readTree(logBody).get("run").get("id").asString();

        mockMvc
                .perform(delete("/api/v1/runs/" + runId).header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());

        mockMvc
                .perform(delete("/api/v1/runs/" + runId).header("Authorization", "Bearer " + userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.delta").value(-50));
    }

    @Test
    void recordsEndpointReturnsTheLifetimeTotalAndBracketRankings() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String access = accessTokenFor("records@example.com");

        mockMvc
                .perform(
                        post("/api/v1/runs")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"date":"2026-03-12","distanceMeters":12000,"durationSeconds":3600}
                                        """))
                .andExpect(status().isCreated());

        mockMvc
                .perform(get("/api/v1/runs/records").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeRunPoints").value(220))
                .andExpect(jsonPath("$.topByDistance[0].distanceMeters").value(12000))
                .andExpect(jsonPath("$.byBracket.D10K[0].distanceMeters").value(12000))
                .andExpect(jsonPath("$.byBracket.D15K").isEmpty());
    }
}
