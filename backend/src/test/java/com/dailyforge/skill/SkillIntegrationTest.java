package com.dailyforge.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
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

/** The Skills screen through its HTTP API, with "Enable edit" left off throughout. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillIntegrationTest {

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
                                {"email":"skills@example.com","password":"a-long-enough-password","displayName":"T","timeZone":"UTC"}
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
        return json.readTree(
                mockMvc.perform(auth(b)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode skill(JsonNode board, String code) {
        for (JsonNode s : board.get("skills")) {
            if (s.get("code").asString().equals(code)) return s;
        }
        throw new AssertionError("no skill " + code);
    }

    @Test
    void everySkillStartsAtLevelOneWithItsDrills() throws Exception {
        JsonNode board = call(get("/api/v1/skills"));

        assertThat(board.get("skills")).hasSize(3);
        JsonNode hand = skill(board, "HANDSTAND");
        assertThat(hand.get("level").asInt()).isEqualTo(1);
        assertThat(hand.get("levels")).hasSize(3);
        assertThat(hand.get("levels").get(0).get("drills")).isNotEmpty();
        assertThat(skill(board, "FLIP").get("safety").asString()).contains("spotter");
    }

    @Test
    void theLevelIsRememberedAndOutOfRangeIsRefused() throws Exception {
        call(put("/api/v1/skills/CROW_LSIT/level").content("{\"level\":2}"));

        assertThat(skill(call(get("/api/v1/skills")), "CROW_LSIT").get("level").asInt()).isEqualTo(2);
        mockMvc.perform(auth(put("/api/v1/skills/CROW_LSIT/level")).content("{\"level\":4}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(auth(put("/api/v1/skills/NOPE/level")).content("{\"level\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void doneTodayPaysFivePointsOnceAndUnmarkingTakesThemBack() throws Exception {
        JsonNode done = call(post("/api/v1/skills/HANDSTAND/done").content("{\"date\":\"" + TODAY + "\"}"));
        assertThat(done.get("points").get("delta").asInt()).isEqualTo(5);
        assertThat(skill(done.get("board"), "HANDSTAND").get("doneOnDate").asBoolean()).isTrue();
        assertThat(skill(done.get("board"), "HANDSTAND").get("daysDone").asInt()).isEqualTo(1);

        JsonNode again = call(post("/api/v1/skills/HANDSTAND/done").content("{\"date\":\"" + TODAY + "\"}"));
        assertThat(again.get("points").get("delta").asInt()).isZero();

        JsonNode undone = call(delete("/api/v1/skills/HANDSTAND/done?date=" + TODAY));
        assertThat(undone.get("points").get("delta").asInt()).isEqualTo(-5);
        assertThat(skill(undone.get("board"), "HANDSTAND").get("doneOnDate").asBoolean()).isFalse();

        // Marking it again pays again: it is done again.
        JsonNode redone = call(post("/api/v1/skills/HANDSTAND/done").content("{\"date\":\"" + TODAY + "\"}"));
        assertThat(redone.get("points").get("delta").asInt()).isEqualTo(5);
    }

    @Test
    void eachSkillPaysOnItsOwn() throws Exception {
        assertThat(call(post("/api/v1/skills/HANDSTAND/done").content("{\"date\":\"" + TODAY + "\"}"))
                        .get("points").get("delta").asInt())
                .isEqualTo(5);
        assertThat(call(post("/api/v1/skills/FLIP/done").content("{\"date\":\"" + TODAY + "\"}"))
                        .get("points").get("delta").asInt())
                .isEqualTo(5);
    }
}
