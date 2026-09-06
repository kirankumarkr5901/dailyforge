package com.dailyforge.points;

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

/**
 * The points endpoints through the real filter chain, with real signed-in users. The
 * cross-user test is the one spec §10 names directly: "an integration test that asserts
 * user A cannot read user B's rows for every resource."
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointsControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

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

    private void debugAward(String access, int amount, String description) throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/points/debug/award")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"category":"HABIT","ruleCode":"HABIT_BASE","amount":%d,"description":"%s"}
                                        """
                                                .formatted(amount, description)))
                .andExpect(status().isOk());
    }

    @Test
    void snapshotRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/points/snapshot")).andExpect(status().isUnauthorized());
    }

    @Test
    void ledgerRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/points/ledger")).andExpect(status().isUnauthorized());
    }

    @Test
    void debugAwardIsReflectedInSnapshotAndLedger() throws Exception {
        String access = accessTokenFor("a@example.com");
        debugAward(access, 25, "Test award");

        mockMvc
                .perform(get("/api/v1/points/snapshot").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25));

        mockMvc
                .perform(get("/api/v1/points/ledger").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(25))
                .andExpect(jsonPath("$.content[0].description").value("Test award"));
    }

    @Test
    void recalculateRebuildsTheCacheAndReturnsTheSnapshot() throws Exception {
        String access = accessTokenFor("b@example.com");
        debugAward(access, 10, "one");
        debugAward(access, 15, "two");

        mockMvc
                .perform(post("/api/v1/points/recalculate").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25));
    }

    /**
     * Spec §10: "Add an integration test that asserts user A cannot read user B's rows
     * for every resource." This is that test, for the points ledger and snapshot.
     */
    @Test
    void userACannotSeeUserBsLedgerOrSnapshot() throws Exception {
        String userA = accessTokenFor("usera@example.com");
        String userB = accessTokenFor("userb@example.com");

        debugAward(userA, 999, "User A's private award");
        // User B has awarded nothing.

        mockMvc
                .perform(get("/api/v1/points/snapshot").header("Authorization", "Bearer " + userB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)); // not 999 — user B's own total only

        JsonNode ledgerForB =
                json.readTree(
                        mockMvc
                                .perform(get("/api/v1/points/ledger").header("Authorization", "Bearer " + userB))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        org.assertj.core.api.Assertions.assertThat(ledgerForB.get("content")).isEmpty();
    }
}
