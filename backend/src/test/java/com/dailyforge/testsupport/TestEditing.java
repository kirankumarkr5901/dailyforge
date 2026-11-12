package com.dailyforge.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every account starts with "Enable edit" off (V27). A test that sets up habits, plans
 * or rewards through the real API turns it on first, the same way the owner would in
 * Settings — the lock itself is covered by {@code EditingLockIntegrationTest}.
 */
public final class TestEditing {

    private TestEditing() {}

    public static void enable(MockMvc mockMvc, String accessToken) throws Exception {
        mockMvc
                .perform(
                        patch("/api/v1/me/settings")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"editingEnabled\":true}"))
                .andExpect(status().isOk());
    }
}
