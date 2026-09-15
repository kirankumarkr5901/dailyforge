package com.dailyforge.insight;

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

/** Home, the heatmap, and the quote endpoint through the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightControllerIntegrationTest {

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
    void everyInsightEndpointRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/home/summary")).andExpect(status().isUnauthorized());
        mockMvc
                .perform(get("/api/v1/insights/heatmap?from=2026-01-01&to=2026-01-31"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/quotes/today")).andExpect(status().isUnauthorized());
    }

    @Test
    void homeSummaryReturnsAQuoteAndAZeroScoreForABrandNewUser() throws Exception {
        String access = accessTokenFor("home@example.com");

        mockMvc
                .perform(get("/api/v1/home/summary").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quote.text").isNotEmpty())
                .andExpect(jsonPath("$.score.total").value(0))
                .andExpect(jsonPath("$.recentLedger").isArray());
    }

    @Test
    void todaysQuoteMatchesTheOneEmbeddedInHomeSummary() throws Exception {
        String access = accessTokenFor("quote@example.com");

        String quoteBody =
                mockMvc
                        .perform(get("/api/v1/quotes/today").header("Authorization", "Bearer " + access))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String summaryBody =
                mockMvc
                        .perform(get("/api/v1/home/summary").header("Authorization", "Bearer " + access))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String quoteId = json.readTree(quoteBody).get("id").asString();
        String summaryQuoteId = json.readTree(summaryBody).get("quote").get("id").asString();
        org.assertj.core.api.Assertions.assertThat(summaryQuoteId).isEqualTo(quoteId);
    }

    @Test
    void heatmapCoversTheRequestedRangeInclusive() throws Exception {
        String access = accessTokenFor("heatmap@example.com");

        mockMvc
                .perform(
                        get("/api/v1/insights/heatmap?from=2026-03-01&to=2026-03-03")
                                .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
