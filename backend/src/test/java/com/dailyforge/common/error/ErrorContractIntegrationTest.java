package com.dailyforge.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The error contract is part of the API, so it is tested like one.
 *
 * The frontend maps `code` to copy, which means a wrong code is a wrong message shown to
 * a user. It also means an unknown endpoint must not arrive as INTERNAL_ERROR: a client
 * that cannot distinguish "does not exist" from "we are broken" retries the wrong things.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorContractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void anUnknownEndpointIsNotFoundRatherThanAServerError() throws Exception {
        mockMvc
                .perform(get("/api/v1/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void everyErrorCarriesTheFullContractShape() throws Exception {
        mockMvc
                .perform(get("/api/v1/nothing"))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.field").doesNotExist())
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    void noStackTraceOrJavaDetailEverReachesTheClient() throws Exception {
        String body =
                mockMvc
                        .perform(get("/api/v1/nothing"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("org.springframework")
                .doesNotContain("com.dailyforge")
                .doesNotContain("\tat ");
    }
}
