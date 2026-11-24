package com.dailyforge.mobility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Guided mobility flows through the HTTP API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobilityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    private String token;

    @BeforeEach
    void signUp() throws Exception {
        clean();
        String body = mockMvc
                .perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"mobility@example.com","password":"a-long-enough-password","displayName":"T","timeZone":"UTC"}
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

    @Test
    void theFourFlowsAreServedWithTheirMoves() throws Exception {
        JsonNode board = call(get("/api/v1/mobility"));

        assertThat(board.get("flows")).hasSize(4);
        JsonNode warm = board.get("flows").get(0);
        assertThat(warm.get("code").asString()).isEqualTo("WARMUP");
        assertThat(warm.get("moves")).hasSize(10);
        assertThat(warm.get("totalSeconds").asInt()).isEqualTo(390);
        assertThat(warm.get("doneToday").asBoolean()).isFalse();
    }

    @Test
    void finishingAFlowPaysFivePointsOncePerDay() throws Exception {
        JsonNode first = call(post("/api/v1/mobility/WARMUP/complete"));
        assertThat(first.get("points").get("delta").asInt()).isEqualTo(5);
        assertThat(first.get("board").get("flows").get(0).get("doneToday").asBoolean()).isTrue();

        JsonNode second = call(post("/api/v1/mobility/WARMUP/complete"));
        assertThat(second.get("points").get("delta").asInt()).isZero();
        assertThat(second.get("board").get("flows").get(0).get("timesDone").asInt()).isEqualTo(1);

        assertThat(call(post("/api/v1/mobility/DESK/complete")).get("points").get("delta").asInt()).isEqualTo(5);
    }

    @Test
    void anUnknownFlowIsNotFound() throws Exception {
        mockMvc.perform(auth(post("/api/v1/mobility/NOPE/complete"))).andExpect(status().isNotFound());
    }
}
