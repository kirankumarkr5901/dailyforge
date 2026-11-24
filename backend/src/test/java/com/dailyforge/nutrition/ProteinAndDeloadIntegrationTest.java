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
        assertThat(day.get("foods")).hasSize(12);
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
