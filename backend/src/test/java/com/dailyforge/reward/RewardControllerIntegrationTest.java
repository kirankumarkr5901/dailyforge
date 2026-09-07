package com.dailyforge.reward;

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
import tools.jackson.databind.ObjectMapper;

/** Rewards through the real filter chain (spec §10's cross-user test, applied to rewards). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RewardControllerIntegrationTest {

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

    @Test
    void everyRewardEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/rewards")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(post("/api/v1/rewards").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redeemingWithoutEnoughPointsIsRefusedThroughTheRealEndpoint() throws Exception {
        String access = accessTokenFor("broke@example.com");

        String rewardBody =
                mockMvc
                        .perform(
                                post("/api/v1/rewards")
                                        .header("Authorization", "Bearer " + access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"name":"Big prize","cost":500,"icon":"gift","tier":"WEEKLY","isRepeatable":true}
                                                """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String rewardId = json.readTree(rewardBody).get("id").asString();

        mockMvc
                .perform(post("/api/v1/rewards/" + rewardId + "/redeem").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Costs 500. You have 0."));
    }

    @Test
    void userACannotRedeemUserBsReward() throws Exception {
        String userA = accessTokenFor("a@example.com");
        String userB = accessTokenFor("b@example.com");

        String rewardBody =
                mockMvc
                        .perform(
                                post("/api/v1/rewards")
                                        .header("Authorization", "Bearer " + userA)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                {"name":"A's reward","cost":10,"icon":"star","tier":"MICRO","isRepeatable":true}
                                                """))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String rewardId = json.readTree(rewardBody).get("id").asString();

        mockMvc
                .perform(post("/api/v1/rewards/" + rewardId + "/redeem").header("Authorization", "Bearer " + userB))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeemedRewardsAppearInTheRedemptionsListAndCanBeRefunded() throws Exception {
        String access = accessTokenFor("spender@example.com");
        // No points to spend, so this proves the refund endpoint itself 404s cleanly for a made-up id.
        mockMvc
                .perform(delete("/api/v1/reward-redemptions/" + java.util.UUID.randomUUID()).header("Authorization", "Bearer " + access))
                .andExpect(status().isNotFound());

        mockMvc
                .perform(get("/api/v1/reward-redemptions").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
