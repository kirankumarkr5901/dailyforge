package com.dailyforge.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.workout.domain.DeloadService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The protein meter and deload weeks, through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProteinAndDeloadIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    private static final String TODAY = LocalDate.now(ZoneOffset.UTC).toString();

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        clean();
        String body = mockMvc
                .perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"protein@example.com","password":"a-long-enough-password","displayName":"T","timeZone":"UTC"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        token = json.readTree(body).get("accessToken").asString();
    }

    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode call(MockHttpServletRequestBuilder b) throws Exception {
        return json.readTree(mockMvc.perform(auth(b)).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode eat(String name, int grams) throws Exception {
        return call(post("/api/v1/protein/logs")
                .content("{\"date\":\"" + TODAY + "\",\"foodName\":\"" + name + "\",\"grams\":" + grams + "}"));
    }

    private void setEditing(boolean on) throws Exception {
        call(patch("/api/v1/me/settings").content("{\"editingEnabled\":" + on + "}"));
    }

    @Test
    void aNewUserStartsWithTheStarterFoodsAnd140g() throws Exception {
        JsonNode day = call(get("/api/v1/protein/day"));

        assertThat(day.get("targetG").asInt()).isEqualTo(140);
        assertThat(day.get("totalG").asInt()).isZero();
        assertThat(day.get("foods")).hasSize(30);
        assertThat(day.get("foods").get(0).get("name").asString()).isEqualTo("2 eggs");
    }

    @Test
    void reachingTheTargetPays10PointsOnceAndAnUndoTakesThemBack() throws Exception {
        assertThat(eat("Chicken 100 g", 100).get("points").get("delta").asInt()).isZero();

        JsonNode hit = eat("Whey scoop", 40);
        assertThat(hit.get("day").get("totalG").asInt()).isEqualTo(140);
        assertThat(hit.get("points").get("delta").asInt()).isEqualTo(10);

        // Already over the target: no second payment.
        JsonNode more = eat("2 eggs", 12);
        assertThat(more.get("points").get("delta").asInt()).isZero();

        // Undo the whey: 112 g, back below, so the 10 points are reversed.
        String wheyId = more.get("day").get("entries").get(1).get("id").asString();
        JsonNode undo = call(delete("/api/v1/protein/logs/" + wheyId));
        assertThat(undo.get("day").get("totalG").asInt()).isEqualTo(112);
        assertThat(undo.get("points").get("delta").asInt()).isEqualTo(-10);
    }

    @Test
    void loweringTheTargetBelowTodaysTotalPaysStraightAway() throws Exception {
        eat("Chicken 100 g", 100);

        JsonNode result = call(put("/api/v1/protein/target").content("{\"targetG\":90}"));

        assertThat(result.get("day").get("targetG").asInt()).isEqualTo(90);
        assertThat(result.get("points").get("delta").asInt()).isEqualTo(10);
    }

    @Test
    void theFoodListIsLockedButLoggingAndTheTargetAreNot() throws Exception {
        mockMvc.perform(auth(post("/api/v1/protein/foods")).content("{\"name\":\"Lentils\",\"grams\":9}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));

        eat("Chicken 100 g", 31);
        call(put("/api/v1/protein/target").content("{\"targetG\":150}"));

        setEditing(true);
        JsonNode food = call(post("/api/v1/protein/foods").content("{\"name\":\"Lentils\",\"grams\":9}"));
        assertThat(food.get("name").asString()).isEqualTo("Lentils");
    }

    @Test
    void deletingEveryFoodDoesNotBringTheStarterListBack() throws Exception {
        setEditing(true);
        for (JsonNode food : call(get("/api/v1/protein/day")).get("foods")) {
            mockMvc.perform(auth(delete("/api/v1/protein/foods/" + food.get("id").asString())))
                    .andExpect(status().isNoContent());
        }

        assertThat(call(get("/api/v1/protein/day")).get("foods")).isEmpty();
    }

    @Test
    void outOfRangeGramsAreRefused() throws Exception {
        mockMvc.perform(auth(post("/api/v1/protein/logs"))
                        .content("{\"date\":\"" + TODAY + "\",\"foodName\":\"x\",\"grams\":0}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(auth(put("/api/v1/protein/target")).content("{\"targetG\":0}"))
                .andExpect(status().is4xxClientError());
    }

    private JsonNode eatOn(String date, String name, int protein, int kcal, int fat) throws Exception {
        return call(post("/api/v1/protein/logs")
                .content("{\"date\":\"" + date + "\",\"foodName\":\"" + name + "\",\"grams\":" + protein
                        + ",\"kcal\":" + kcal + ",\"fatG\":" + fat + "}"));
    }

    @Test
    void caloriesAndFatAddUpAlongsideProtein() throws Exception {
        JsonNode day = call(get("/api/v1/protein/day"));
        assertThat(day.get("calorieBudget").asInt()).isEqualTo(2250);
        assertThat(day.get("fatTargetG").asInt()).isEqualTo(70);
        assertThat(day.get("foods").get(0).get("kcal").asInt()).isEqualTo(140);

        eatOn(TODAY, "Rice bowl", 0, 350, 1);
        JsonNode after = eatOn(TODAY, "2 eggs", 12, 140, 10).get("day");

        assertThat(after.get("totalKcal").asInt()).isEqualTo(490);
        assertThat(after.get("totalFatG").asInt()).isEqualTo(11);
        assertThat(after.get("totalG").asInt()).isEqualTo(12);
    }

    @Test
    void carbsFiberAndSugarAddUpWithTheirOwnTargets() throws Exception {
        JsonNode day = call(get("/api/v1/protein/day"));
        assertThat(day.get("carbsTargetG").asInt()).isEqualTo(250);
        assertThat(day.get("fiberTargetG").asInt()).isEqualTo(30);
        assertThat(day.get("sugarLimitG").asInt()).isEqualTo(50);
        assertThat(day.get("foods").get(0).get("carbsG").asInt()).isEqualTo(1);

        JsonNode after = call(post("/api/v1/protein/logs").content("{\"date\":\"" + TODAY
                + "\",\"foodName\":\"Oats\",\"grams\":10,\"kcal\":300,\"fatG\":5,\"carbsG\":54,\"fiberG\":8,\"sugarG\":2}"))
                .get("day");
        assertThat(after.get("totalCarbsG").asInt()).isEqualTo(54);
        assertThat(after.get("totalFiberG").asInt()).isEqualTo(8);
        assertThat(after.get("totalSugarG").asInt()).isEqualTo(2);
        assertThat(after.get("entries").get(0).get("fiberG").asInt()).isEqualTo(8);

        JsonNode targets = call(put("/api/v1/protein/target")
                .content("{\"carbsTargetG\":200,\"fiberTargetG\":35,\"sugarLimitG\":40}")).get("day");
        assertThat(targets.get("carbsTargetG").asInt()).isEqualTo(200);
        assertThat(targets.get("fiberTargetG").asInt()).isEqualTo(35);
        assertThat(targets.get("sugarLimitG").asInt()).isEqualTo(40);
        assertThat(targets.get("targetG").asInt()).isEqualTo(140);

        mockMvc.perform(auth(put("/api/v1/protein/target")).content("{\"fiberTargetG\":1}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void aMealIsSavedWithEditingOnAndLogsAllItsItemsInOneTap() throws Exception {
        String meal = "{\"name\":\"Big breakfast\",\"items\":["
                + "{\"foodName\":\"2 eggs\",\"grams\":12,\"kcal\":140,\"fatG\":10,\"carbsG\":1},"
                + "{\"foodName\":\"Whey scoop\",\"grams\":130,\"kcal\":600,\"fatG\":8,\"carbsG\":15}]}";
        mockMvc.perform(auth(post("/api/v1/protein/meals")).content(meal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));

        setEditing(true);
        JsonNode created = json.readTree(mockMvc.perform(auth(post("/api/v1/protein/meals")).content(meal))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.get("proteinG").asInt()).isEqualTo(142);
        assertThat(created.get("items")).hasSize(2);
        setEditing(false);

        // Logging needs no editing, adds both entries, and pays the protein target once.
        JsonNode logged = call(post("/api/v1/protein/meals/" + created.get("id").asString() + "/log")
                .content("{\"date\":\"" + TODAY + "\"}"));
        assertThat(logged.get("day").get("entries")).hasSize(2);
        assertThat(logged.get("day").get("totalG").asInt()).isEqualTo(142);
        assertThat(logged.get("day").get("totalCarbsG").asInt()).isEqualTo(16);
        assertThat(logged.get("points").get("delta").asInt()).isEqualTo(10);

        assertThat(call(get("/api/v1/protein/meals"))).hasSize(1);
        setEditing(true);
        mockMvc.perform(auth(delete("/api/v1/protein/meals/" + created.get("id").asString())))
                .andExpect(status().isNoContent());
        assertThat(call(get("/api/v1/protein/meals"))).isEmpty();
    }

    private JsonNode water(int delta) throws Exception {
        return call(post("/api/v1/protein/water").content("{\"date\":\"" + TODAY + "\",\"deltaMl\":" + delta + "}"));
    }

    @Test
    void waterPaysFifteenOnceTheTargetIsReachedAndUndoTakesItBack() throws Exception {
        assertThat(call(get("/api/v1/protein/day")).get("waterTargetMl").asInt()).isEqualTo(3000);

        assertThat(water(2000).get("points").get("delta").asInt()).isZero();
        assertThat(water(500).get("day").get("waterMl").asInt()).isEqualTo(2500);
        JsonNode hit = water(500);
        assertThat(hit.get("day").get("waterMl").asInt()).isEqualTo(3000);
        assertThat(hit.get("points").get("delta").asInt()).isEqualTo(15);
        assertThat(water(250).get("points").get("delta").asInt()).isZero();

        // Undo twice: back below the target, so the 15 go back.
        water(-250);
        assertThat(water(-250).get("points").get("delta").asInt()).isEqualTo(-15);

        // Never below zero.
        for (int i = 0; i < 2; i++) water(-2000);
        assertThat(water(-250).get("day").get("waterMl").asInt()).isZero();

        assertThat(call(put("/api/v1/protein/target").content("{\"waterTargetMl\":2000}")).get("day").get("waterTargetMl").asInt())
                .isEqualTo(2000);
    }

    @Test
    void anEntryWithNothingInItIsRefused() throws Exception {
        mockMvc.perform(auth(post("/api/v1/protein/logs"))
                        .content("{\"date\":\"" + TODAY + "\",\"foodName\":\"x\",\"grams\":0,\"kcal\":0,\"fatG\":0}"))
                .andExpect(status().is4xxClientError());
    }

    /** Today is never "within budget" yet; a closed day is judged on what was logged. */
    @Test
    void aClosedDayWithinTheBudgetPaysTenAndTodayNeverDoes() throws Exception {
        assertThat(eatOn(TODAY, "Lunch", 30, 600, 20).get("points").get("delta").asInt()).isZero();

        String yesterday = LocalDate.parse(TODAY).minusDays(1).toString();
        assertThat(eatOn(yesterday, "Lunch", 30, 600, 20).get("points").get("delta").asInt()).isEqualTo(10);

        // Going over the budget for that day takes the 10 back.
        assertThat(eatOn(yesterday, "Feast", 0, 2000, 80).get("points").get("delta").asInt()).isEqualTo(-10);
    }

    @Test
    void theBudgetAndFatTargetCanBeChanged() throws Exception {
        JsonNode result = call(put("/api/v1/protein/target").content("{\"calorieBudget\":2000,\"fatTargetG\":60}"));

        assertThat(result.get("day").get("calorieBudget").asInt()).isEqualTo(2000);
        assertThat(result.get("day").get("fatTargetG").asInt()).isEqualTo(60);
        assertThat(result.get("day").get("targetG").asInt()).isEqualTo(140);
        mockMvc.perform(auth(put("/api/v1/protein/target")).content("{\"calorieBudget\":100}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deloadIsSwitchedOnWithEditingOffAndShowsOnTheSession() throws Exception {
        JsonNode on = call(put("/api/v1/workouts/deload").content("{\"date\":\"" + TODAY + "\",\"deload\":true}"));
        assertThat(on.get("deload").asBoolean()).isTrue();

        assertThat(call(get("/api/v1/workouts/session?date=" + TODAY)).get("deload").asBoolean()).isTrue();

        call(put("/api/v1/workouts/deload").content("{\"date\":\"" + TODAY + "\",\"deload\":false}"));
        assertThat(call(get("/api/v1/workouts/session?date=" + TODAY)).get("deload").asBoolean()).isFalse();
    }

    @Test
    void deloadHalvesSetsRoundingUp() {
        assertThat(DeloadService.deloadSets(4)).isEqualTo(2);
        assertThat(DeloadService.deloadSets(3)).isEqualTo(2);
        assertThat(DeloadService.deloadSets(1)).isEqualTo(1);
        assertThat(DeloadService.deloadSets(null)).isNull();
    }
}
