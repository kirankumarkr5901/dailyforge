package com.dailyforge.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * The anonymous-browsing contract (spec §4.1).
 *
 * The frontend's whole "open the login sheet, then replay what the user was doing"
 * behaviour depends on a write refusing with exactly {@code AUTH_REQUIRED} and a 401.
 * If this drifts, the user silently loses the input they just typed — so it is pinned
 * here rather than left to the security configuration to be self-evident.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonymousBrowsingTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anonymousReadsAreAllowedThrough() throws Exception {
        // Reaches the handler rather than the filter chain: 404 means routing, not refusal.
        mockMvc.perform(get("/api/v1/does-not-exist-yet")).andExpect(status().isNotFound());
    }

    @Test
    void anonymousWritesAreRefusedWithTheAuthRequiredContract() throws Exception {
        mockMvc
                .perform(post("/api/v1/habits").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Sign in to do that."))
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    void everyWritingMethodIsRefusedTheSameWay() throws Exception {
        mockMvc
                .perform(patch("/api/v1/habits/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc
                .perform(delete("/api/v1/habits/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void signingInDoesNotRequireBeingSignedIn() throws Exception {
        mockMvc.perform(get("/api/v1/auth/capabilities")).andExpect(status().isOk());

        // Reached the handler and failed validation, rather than being refused by the chain.
        mockMvc
                .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aGarbageTokenIsRefusedRatherThanTrusted() throws Exception {
        mockMvc
                .perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthStaysOpenSoThePlatformCanProbeIt() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
