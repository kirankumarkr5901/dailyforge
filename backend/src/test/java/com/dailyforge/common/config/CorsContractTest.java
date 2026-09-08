package com.dailyforge.common.config;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The CORS preflight contract.
 *
 * This is pinned by a test because the failure mode is invisible everywhere it would be
 * convenient to notice. In development the Angular dev server proxies {@code /api}, so
 * the frontend and the API share an origin and no preflight happens at all; every test
 * that talks to a controller does so in-process. A header missing from the allow-list
 * therefore breaks nothing until the frontend and the API are on different hosts —
 * which is to say, it breaks first in production, on every edit.
 *
 * {@code If-Match} carries the version an edit was made against (see StaleWrite) and
 * {@code Idempotency-Key} is on every write (spec §4.5). Both must survive preflight.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "dailyforge.cors.allowed-origins=https://dailyforge.example")
class CorsContractTest {

    private static final String ORIGIN = "https://dailyforge.example";

    @Autowired private MockMvc mockMvc;

    /**
     * Asserts the <em>contents</em> of Access-Control-Allow-Headers, not the status.
     *
     * A preflight for a header the server does not allow still answers 200 — it simply
     * leaves that header out of the allow-list, and the <em>browser</em> is what refuses
     * to send the real request afterwards. A test that only checked the status passed
     * happily while If-Match was missing, which is precisely the failure this file
     * exists to catch.
     */
    @Test
    void theHeadersEveryWriteSendsSurvivePreflight() throws Exception {
        mockMvc
                .perform(
                        options("/api/v1/rewards/00000000-0000-0000-0000-000000000000")
                                .header("Origin", ORIGIN)
                                .header("Access-Control-Request-Method", "PUT")
                                .header("Access-Control-Request-Headers", "authorization,content-type,if-match,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("if-match")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("idempotency-key")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
    }

    @Test
    void theEditMethodsAreAllowedFromTheConfiguredOrigin() throws Exception {
        for (String method : new String[] {"PATCH", "PUT", "DELETE", "POST"}) {
            mockMvc
                    .perform(
                            options("/api/v1/habits/00000000-0000-0000-0000-000000000000")
                                    .header("Origin", ORIGIN)
                                    .header("Access-Control-Request-Method", method))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        }
    }

    @Test
    void anUnknownOriginIsRefused() throws Exception {
        mockMvc
                .perform(
                        options("/api/v1/rewards")
                                .header("Origin", "https://not-our-site.example")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
