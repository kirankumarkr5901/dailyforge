package com.dailyforge.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** "Try the demo", through the real filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private DemoService demo;
    @Autowired private IdentityService identity;

    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private JsonNode tryTheDemo() throws Exception {
        return json.readTree(mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode read(String path, String token) throws Exception {
        return json.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    @Test
    void oneTapOpensAFilledDemoAccountAndEveryVisitorSharesIt() throws Exception {
        JsonNode first = tryTheDemo();
        String token = first.get("accessToken").asString();

        assertThat(read("/api/v1/habits", token).size()).isEqualTo(3);
        assertThat(read("/api/v1/workout-plans", token).size()).isEqualTo(1);
        assertThat(read("/api/v1/protein/day", token).get("entries").size()).isPositive();

        JsonNode second = tryTheDemo();
        assertThat(read("/api/v1/me", second.get("accessToken").asString()).get("id").asString())
                .isEqualTo(read("/api/v1/me", token).get("id").asString());
        assertThat(read("/api/v1/habits", second.get("accessToken").asString()).size()).isEqualTo(3); // not seeded twice
    }

    @Test
    void theDemoCannotTurnOnNotifications() throws Exception {
        String token = tryTheDemo().get("accessToken").asString();
        mockMvc.perform(post("/api/v1/push/subscriptions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example/x\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theNightlyResetRemovesTheDemoAccount() throws Exception {
        tryTheDemo();
        assertThat(identity.demoUserId()).isPresent();
        demo.reset();
        assertThat(identity.demoUserId()).isEmpty();
    }
}
