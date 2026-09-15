package com.dailyforge.workout;

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

/** The workout endpoints through the real filter chain (spec §10's cross-user test, applied to workouts). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(WorkoutClockTestConfig.class)
@ActiveProfiles("test")
class WorkoutControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private WorkoutClockTestConfig.MutableClock clock;

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

    private String createExercise(String access, String name) throws Exception {
        String body =
                mockMvc
                        .perform(
                                post("/api/v1/exercises")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"name":"%s","kind":"STRENGTH","equipment":"BARBELL","muscleGroups":["chest"],"isElite":false}
                                                """
                                                        .formatted(name)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asString();
    }

    @Test
    void everyWorkoutEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/exercises")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workout-plans")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(post("/api/v1/workouts/sets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingASetThroughTheRealEndpointsAwardsPointsAndAPr() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String access = accessTokenFor("lifter@example.com");
        String exerciseId = createExercise(access, "Bench press");

        mockMvc
                .perform(
                        post("/api/v1/workouts/sets")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"date":"2026-03-12","exerciseId":"%s","enteredWeight":50,"weightMode":"COMBINED","reps":5}
                                        """
                                                .formatted(exerciseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.set.totalWeightKg").value(50))
                // 1 (WORKOUT_SET) + round(1.2 * (50/5)) = 13
                .andExpect(jsonPath("$.points.delta").value(13));
    }

    @Test
    void deletingASetReversesItsPoints() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String access = accessTokenFor("deleter@example.com");
        String exerciseId = createExercise(access, "Row");

        String logBody =
                mockMvc
                        .perform(
                                post("/api/v1/workouts/sets")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"date":"2026-03-12","exerciseId":"%s","enteredWeight":30,"weightMode":"COMBINED","reps":8}
                                                """
                                                        .formatted(exerciseId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String setId = json.readTree(logBody).get("set").get("id").asString();

        mockMvc
                .perform(delete("/api/v1/workouts/sets/" + setId).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                // 1 (WORKOUT_SET) + round(1.2 * (30/5)) = 8, entirely reversed
                .andExpect(jsonPath("$.points.delta").value(-8));
    }

    @Test
    void loggingOutsideTheEditWindowIsRefusedWithEntryLocked() throws Exception {
        String access = accessTokenFor("stale@example.com");
        String exerciseId = createExercise(access, "Curl");

        mockMvc
                .perform(
                        post("/api/v1/workouts/sets")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"date":"2020-01-01","exerciseId":"%s","enteredWeight":10,"weightMode":"SINGLE","reps":10}
                                        """
                                                .formatted(exerciseId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENTRY_LOCKED"));
    }

    @Test
    void userACannotEditOrDeleteUserBsSet() throws Exception {
        setToday(java.time.LocalDate.of(2026, 3, 12));
        String userA = accessTokenFor("usera@example.com");
        String userB = accessTokenFor("userb@example.com");
        String exerciseId = createExercise(userA, "Squat");

        String logBody =
                mockMvc
                        .perform(
                                post("/api/v1/workouts/sets")
                                        .header("Authorization", "Bearer " + userA)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"date":"2026-03-12","exerciseId":"%s","enteredWeight":60,"weightMode":"COMBINED","reps":5}
                                                """
                                                        .formatted(exerciseId)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String setId = json.readTree(logBody).get("set").get("id").asString();

        mockMvc
                .perform(delete("/api/v1/workouts/sets/" + setId).header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAPlanAndAddingAnExerciseWorksThroughTheRealEndpoints() throws Exception {
        String access = accessTokenFor("planner@example.com");
        String exerciseId = createExercise(access, "Deadlift");

        String planBody =
                mockMvc
                        .perform(
                                post("/api/v1/workout-plans")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"name":"Full body","dayCount":2}
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.isActive").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String planId = json.readTree(planBody).get("id").asString();

        mockMvc
                .perform(
                        post("/api/v1/workout-plans/" + planId + "/exercises")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"exerciseId":"%s","dayIndex":1,"targetSets":3,"targetReps":8}
                                        """
                                                .formatted(exerciseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dayIndex").value(1));
    }
}
