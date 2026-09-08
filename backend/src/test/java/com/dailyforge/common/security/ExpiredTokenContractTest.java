package com.dailyforge.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Which 401 the server returns, and why the distinction is not cosmetic.
 *
 * The frontend answers these two codes in opposite ways: AUTH_TOKEN_EXPIRED it fixes
 * silently by refreshing, AUTH_REQUIRED it answers by asking the user to sign in. For a
 * long time both cases returned AUTH_REQUIRED, so a merely-expired access token was
 * reported as "you are not signed in". The refresh path never ran, and every session
 * ended fifteen minutes after it began — with the user's own name and data still on
 * screen, because nothing had actually gone wrong with the session.
 *
 * Pinned here because nothing else would catch it: both answers are a 401 with a valid
 * error body, every endpoint behaves correctly, and the failure only shows up as a
 * person being signed out mid-workout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpiredTokenContractTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void aRejectedTokenIsReportedAsExpiredSoTheClientRefreshes() throws Exception {
        mockMvc
                .perform(get("/api/v1/me").header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void noTokenAtAllIsReportedAsAuthRequiredSoTheClientAsksForSignIn() throws Exception {
        mockMvc
                .perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    /**
     * Signing in must work even while a dead token is still in storage.
     *
     * The client used to attach its access token to every call including /auth/login,
     * so once that token expired the resource server rejected the login request before
     * the handler ran: the user was locked out by the very credential they were trying
     * to replace, with clearing site data the only way back. The client no longer sends
     * it, and this checks the endpoint would survive it anyway.
     */
    @Test
    void loginStillWorksWhenARejectedTokenIsPresented() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .header("Authorization", "Bearer not-a-valid-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password-here\"}"))
                // Reaches the handler and fails on the credentials, rather than being
                // refused by the token filter first.
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }
}
