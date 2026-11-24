package com.dailyforge.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyforge.body.domain.BodyMetric;
import com.dailyforge.body.domain.BodyMetricService;
import com.dailyforge.identity.repo.RefreshTokenRepository;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.testsupport.TestUsers;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Tape measurements next to the weigh-in. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodyMeasurementsTest {

    @Autowired private BodyMetricService body;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Test
    void measurementsAreSavedAndASecondLogThatDayKeepsThem() {
        UUID user = TestUsers.create(users, settings);
        body.upsert(user, TODAY, new BigDecimal("80"), null, null, null,
                new BodyMetric.Measurements(new BigDecimal("84.5"), new BigDecimal("100"), null, null, null));

        // Later that day: weight again, no tape.
        BodyMetric metric = body.upsert(user, TODAY, new BigDecimal("79.8"), null, null, null, BodyMetric.Measurements.NONE);

        assertThat(metric.getWeightKg()).isEqualByComparingTo("79.8");
        assertThat(metric.getMeasurements().waistCm()).isEqualByComparingTo("84.5");
        assertThat(metric.getMeasurements().chestCm()).isEqualByComparingTo("100");
        assertThat(metric.getMeasurements().armCm()).isNull();
    }

    @Test
    void anImpossibleMeasurementIsRefused() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tape-" + UUID.randomUUID() + "@example.com\",\"password\":\"a-long-enough-password\",\"displayName\":\"T\",\"timeZone\":\"UTC\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("accessToken").asString();

        mockMvc.perform(post("/api/v1/body-metrics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + TODAY + "\",\"weightKg\":80,\"waistCm\":5}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/body-metrics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + TODAY + "\",\"weightKg\":80,\"waistCm\":84,\"armCm\":35}"))
                .andExpect(status().isCreated());
    }
}
