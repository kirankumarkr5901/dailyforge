package com.dailyforge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * The identity module end to end, through the real filter chain.
 *
 * These go through MockMvc rather than calling the service directly, because half of
 * what M1 promises lives in the security configuration — anonymous reads, refused
 * writes, the shape of the 401 — and a service-level test would not see any of it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    @BeforeEach
    @AfterEach
    void clean() {
        // Both ends: the test profile shares one in-memory database across classes, so
        // leaving rows behind turns an unrelated suite's assertions into a lottery.
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private JsonNode signup(String email, String password) throws Exception {
        String body =
                mockMvc
                        .perform(
                                post("/api/v1/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"%s","password":"%s","displayName":"Kiran","timeZone":"Asia/Kolkata"}
                                                """
                                                        .formatted(email, password)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    @Test
    void signupIssuesASessionAndCreatesSettings() throws Exception {
        JsonNode response = signup("kiran@example.com", "a-long-enough-password");

        assertThat(response.get("accessToken").asString()).isNotBlank();
        assertThat(response.get("refreshToken").asString()).isNotBlank();
        assertThat(response.get("user").get("email").asString()).isEqualTo("kiran@example.com");
        assertThat(response.get("user").get("hasPassword").asBoolean()).isTrue();
        assertThat(response.get("user").get("googleLinked").asBoolean()).isFalse();
        // The time zone captured at signup is what every streak will later resolve through.
        assertThat(response.get("user").get("settings").get("timeZone").asString())
                .isEqualTo("Asia/Kolkata");
    }

    @Test
    void meTodayResolvesTheSignedInUsersLocalDateServerSide() throws Exception {
        JsonNode response = signup("today@example.com", "a-long-enough-password");
        String access = response.get("accessToken").asString();

        mockMvc
                .perform(get("/api/v1/me/today").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").isNotEmpty());
    }

    @Test
    void emailIsNormalisedSoCaseDoesNotCreateASecondAccount() throws Exception {
        signup("Kiran@Example.com", "a-long-enough-password");

        mockMvc
                .perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"kiran@example.com","password":"another-long-password","displayName":"Other","timeZone":"UTC"}
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_TAKEN"));

        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findAll().getFirst().getEmail()).isEqualTo("kiran@example.com");
    }

    @Test
    void loginSucceedsRegardlessOfEmailCasing() throws Exception {
        signup("kiran@example.com", "a-long-enough-password");

        mockMvc
                .perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"  KIRAN@EXAMPLE.COM  ","password":"a-long-enough-password"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void aWrongPasswordAndAnUnknownAddressAnswerIdentically() throws Exception {
        signup("kiran@example.com", "a-long-enough-password");

        String wrongPassword =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"kiran@example.com","password":"not-the-password"}
                                                """))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String unknownAddress =
                mockMvc
                        .perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email":"nobody@example.com","password":"not-the-password"}
                                                """))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Any difference here turns the login form into a way to discover who has an account.
        assertThat(wrongPassword).isEqualTo(unknownAddress);
    }

    @Test
    void theStoredPasswordIsAHashAndNeverTheOriginal() throws Exception {
        signup("kiran@example.com", "a-long-enough-password");

        String stored = users.findAll().getFirst().getPasswordHash();
        assertThat(stored).isNotNull().doesNotContain("a-long-enough-password").startsWith("$2");
    }

    @Test
    void aRefreshTokenIsStoredOnlyAsAHash() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String raw = session.get("refreshToken").asString();

        assertThat(refreshTokens.count()).isEqualTo(1);
        assertThat(refreshTokens.findAll().getFirst().getTokenHash()).isNotEqualTo(raw);
        // A database dump must not be replayable as a login.
        assertThat(refreshTokens.findByTokenHash(raw)).isEmpty();
    }

    @Test
    void refreshRotatesTheTokenAndRetiresTheOldOne() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String first = session.get("refreshToken").asString();

        String body =
                mockMvc
                        .perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String second = json.readTree(body).get("refreshToken").asString();
        assertThat(second).isNotEqualTo(first);

        // The retired token must not work a second time.
        mockMvc
                .perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void reusingARetiredTokenEndsEverySessionForThatUser() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String first = session.get("refreshToken").asString();

        String body =
                mockMvc
                        .perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String second = json.readTree(body).get("refreshToken").asString();

        // Replaying the old one looks like theft, so the live token dies with it.
        mockMvc
                .perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(first)))
                .andExpect(status().isUnauthorized());

        mockMvc
                .perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(second)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String refresh = session.get("refreshToken").asString();

        mockMvc
                .perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isNoContent());

        // Signing out twice is a success from the user's point of view.
        mockMvc
                .perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isNoContent());

        mockMvc
                .perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresTheAccessTokenAndReturnsTheProfile() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String access = session.get("accessToken").asString();

        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

        mockMvc
                .perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("kiran@example.com"))
                .andExpect(jsonPath("$.settings.weekStart").value("MONDAY"));
    }

    @Test
    void settingsRejectAnUnknownTimeZoneRatherThanStoringIt() throws Exception {
        JsonNode session = signup("kiran@example.com", "a-long-enough-password");
        String access = session.get("accessToken").asString();

        mockMvc
                .perform(
                        patch("/api/v1/me/settings")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"timeZone\":\"Mars/Olympus_Mons\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.field").value("timeZone"));

        mockMvc
                .perform(
                        patch("/api/v1/me/settings")
                                .header("Authorization", "Bearer " + access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"timeZone\":\"Europe/London\",\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/London"))
                .andExpect(jsonPath("$.theme").value("DARK"));
    }

    @Test
    void aShortPasswordIsRefusedWithCopyThatSaysWhatToDo() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"email":"kiran@example.com","password":"short","displayName":"Kiran","timeZone":"UTC"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.message").value("Use at least 10 characters."));
    }

    @Test
    void googleSignInIsRefusedHonestlyWhenNotConfigured() throws Exception {
        // No GOOGLE_CLIENT_ID in the test profile, so the feature is dark rather than broken.
        mockMvc
                .perform(
                        post("/api/v1/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idToken\":\"anything\",\"timeZone\":\"UTC\"}"))
                .andExpect(status().isNotImplemented());

        mockMvc
                .perform(get("/api/v1/auth/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordEnabled").value(true))
                .andExpect(jsonPath("$.googleEnabled").value(false));
    }
}
