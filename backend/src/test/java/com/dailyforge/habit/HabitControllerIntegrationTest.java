package com.dailyforge.habit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * The habit endpoints through the real filter chain. The cross-user test is the one
 * spec §10 names directly — "an integration test that asserts user A cannot read user
 * B's rows for every resource" — applied here to habits.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(HabitClockTestConfig.class)
@ActiveProfiles("test")
class HabitControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private HabitClockTestConfig.MutableClock clock;

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

    private String createHabit(String access, String name) throws Exception {
        String body =
                mockMvc
                        .perform(
                                post("/api/v1/habits")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"name":"%s","icon":"book","points":10,"type":"NORMAL"}
                                                """
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void everyHabitEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/habits")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(post("/api/v1/habits").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/habits/board?date=2026-03-12")).andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAHabitDefaultsBonusFieldsFromConfiguration() throws Exception {
        String access = accessTokenFor("a@example.com");

        mockMvc
                .perform(
                        post("/api/v1/habits")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Read","icon":"book","points":10,"type":"NORMAL"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseBonus").value(20))
                .andExpect(jsonPath("$.bonusMultiplier").value(1.5))
                .andExpect(jsonPath("$.scheduleDays").value(127));
    }

    @Test
    void loggingAndUnloggingRoundTripsThroughTheRealEndpoints() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String access = accessTokenFor("b@example.com");
        String habitId = createHabit(access, "Read");

        mockMvc
                .perform(
                        post("/api/v1/habits/" + habitId + "/logs")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-03-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.delta").value(10));

        mockMvc
                .perform(
                        delete("/api/v1/habits/" + habitId + "/logs/2026-03-12")
                                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.delta").value(-10));
    }

    @Test
    void loggingOutsideTheEditWindowIsRefusedWithHabitLocked() throws Exception {
        String access = accessTokenFor("c@example.com");
        String habitId = createHabit(access, "Read");

        mockMvc
                .perform(
                        post("/api/v1/habits/" + habitId + "/logs")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2020-01-01\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HABIT_LOCKED"));
    }

    @Test
    void userACannotReadWriteOrDeleteUserBsHabit() throws Exception {
        String userA = accessTokenFor("usera@example.com");
        String userB = accessTokenFor("userb@example.com");
        String habitOfA = createHabit(userA, "Private habit");

        // B cannot see A's habit in their own list.
        mockMvc
                .perform(get("/api/v1/habits").header("Authorization", "Bearer " + userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // B cannot update it.
        mockMvc
                .perform(
                        patch("/api/v1/habits/" + habitOfA)
                                .header("Authorization", "Bearer " + userB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());

        // B cannot log it.
        mockMvc
                .perform(
                        post("/api/v1/habits/" + habitOfA + "/logs")
                                .header("Authorization", "Bearer " + userB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"2026-03-12\"}"))
                .andExpect(status().isNotFound());

        // B cannot delete it.
        mockMvc
                .perform(delete("/api/v1/habits/" + habitOfA).header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());

        // A's habit is still intact and reachable by A.
        mockMvc
                .perform(get("/api/v1/habits").header("Authorization", "Bearer " + userA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Private habit"));
    }

    @Test
    void bonusPreviewMatchesTheSpecsOwnWorkedExampleAndNeedsNoSignIn() throws Exception {
        // Spec §8.2: "7 days -> 20, 14 days -> 30, 21 days -> 45, 28 days -> 68" at the
        // default baseBonus 20 / multiplier 1.5. A GET is anonymous browsing (spec §4.1)
        // — no Authorization header is sent here, deliberately.
        mockMvc
                .perform(get("/api/v1/habits/bonus-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstFourBonuses[0]").value(20))
                .andExpect(jsonPath("$.firstFourBonuses[1]").value(30))
                .andExpect(jsonPath("$.firstFourBonuses[2]").value(45))
                .andExpect(jsonPath("$.firstFourBonuses[3]").value(68));
    }
}
