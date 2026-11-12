package com.dailyforge.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.testsupport.TestEditing;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/** "Enable edit": structure is locked while it is off; logging never is. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditingLockIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;

    private static final String HABIT =
            """
            {"name":"Read","icon":"book","points":10,"type":"NORMAL"}
            """;

    @BeforeEach
    @AfterEach
    void clean() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private String signUp(String email) throws Exception {
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

    private void setEditing(String token, boolean on) throws Exception {
        mockMvc
                .perform(
                        patch("/api/v1/me/settings")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"editingEnabled\":" + on + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editingEnabled").value(on));
    }

    @Test
    void aNewAccountStartsLockedAndStructuralChangesAreRefused() throws Exception {
        String token = signUp("locked@example.com");

        mockMvc
                .perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.editingEnabled").value(false));

        mockMvc
                .perform(
                        post("/api/v1/habits")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(HABIT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));

        mockMvc
                .perform(
                        post("/api/v1/rewards")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Treat","cost":50,"icon":"gift","tier":"MICRO","isRepeatable":true}
                                        """))
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));
    }

    @Test
    void loggingStillWorksWhileTheStructureIsLocked() throws Exception {
        String token = signUp("logger@example.com");
        setEditing(token, true);
        String created =
                mockMvc
                        .perform(
                                post("/api/v1/habits")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(HABIT))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String habitId = json.readTree(created).get("id").asString();
        setEditing(token, false);

        String today = LocalDate.now(ZoneOffset.UTC).toString();
        mockMvc
                .perform(
                        post("/api/v1/habits/" + habitId + "/logs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"date\":\"" + today + "\"}"))
                .andExpect(status().isOk());
        mockMvc
                .perform(delete("/api/v1/habits/" + habitId + "/logs/" + today).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Renaming, reordering and deleting are structure, so they wait for the switch.
        mockMvc
                .perform(
                        patch("/api/v1/habits/" + habitId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Read more\"}"))
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));
        mockMvc
                .perform(
                        patch("/api/v1/habits/order")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"orderedIds\":[\"" + habitId + "\"]}"))
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));
        mockMvc
                .perform(delete("/api/v1/habits/" + habitId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value("EDITING_LOCKED"));
    }

    @Test
    void anAnonymousRequestIsToldToSignInRatherThanThatEditingIsOff() throws Exception {
        mockMvc
                .perform(post("/api/v1/habits").contentType(MediaType.APPLICATION_JSON).content(HABIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void turningEditingOnAllowsStructuralChanges() throws Exception {
        String token = signUp("editor@example.com");
        TestEditing.enable(mockMvc, token);
        mockMvc
                .perform(
                        post("/api/v1/habits")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(HABIT))
                .andExpect(status().isCreated());
    }
}
