package com.dailyforge.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import com.dailyforge.job.domain.JobApplication;
import com.dailyforge.job.domain.JobService;
import com.dailyforge.job.domain.JobSource;
import com.dailyforge.job.domain.JobStatus;
import com.dailyforge.testsupport.TestUsers;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The job pipeline (spec §8.7). Points are disabled by default (spec §13.9). */
@SpringBootTest
@ActiveProfiles("test")
class JobServiceTest {

    @Autowired private JobService jobs;
    @Autowired private UserRepository users;
    @Autowired private UserSettingsRepository settings;

    @Test
    void creatingAnApplicationWritesTheInitialAppliedEvent() {
        UUID user = TestUsers.create(users, settings);

        JobApplication app =
                jobs.create(user, "Acme", "Engineer", null, "Remote", null, null, JobSource.APPLIED, null, null, LocalDate.of(2026, 3, 1));

        assertThat(app.getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(jobs.timeline(app.getId(), user)).hasSize(1);
        assertThat(jobs.timeline(app.getId(), user).get(0).getToStatus()).isEqualTo(JobStatus.APPLIED);
    }

    @Test
    void transitioningWritesAnEventAndAdvancesTheStatus() {
        UUID user = TestUsers.create(users, settings);
        JobApplication app =
                jobs.create(user, "Acme", "Engineer", null, null, null, null, JobSource.APPLIED, null, null, LocalDate.of(2026, 3, 1));

        jobs.transition(app.getId(), user, JobStatus.INTERVIEW, 1, "First round", LocalDate.of(2026, 3, 5));

        JobApplication reloaded = jobs.requireOwned(app.getId(), user);
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.INTERVIEW);
        assertThat(reloaded.getCurrentRound()).isEqualTo(1);
        assertThat(jobs.timeline(app.getId(), user)).hasSize(2);
    }

    @Test
    void metricsCountResponsesAndInterviewsCorrectly() {
        UUID user = TestUsers.create(users, settings);
        JobApplication responded =
                jobs.create(user, "Acme", "Engineer", null, null, null, null, JobSource.APPLIED, null, null, LocalDate.of(2026, 3, 1));
        jobs.transition(responded.getId(), user, JobStatus.INTERVIEW, 1, null, LocalDate.of(2026, 3, 6)); // 5 days to respond

        jobs.create(user, "Ghost Co", "Engineer", null, null, null, null, JobSource.APPLIED, null, null, LocalDate.of(2026, 3, 1)); // never responds

        var metrics = jobs.metrics(user);

        assertThat(metrics.responseRate()).isEqualTo(0.5);
        assertThat(metrics.averageDaysToFirstResponse()).isEqualTo(5.0);
        assertThat(metrics.interviewsPerApplication()).isEqualTo(0.5); // 1 interview event / 2 applications
    }

    @Test
    void oneUserCannotReachAnotherUsersApplication() {
        UUID userA = TestUsers.create(users, settings);
        UUID userB = TestUsers.create(users, settings);
        JobApplication app =
                jobs.create(userA, "Acme", "Engineer", null, null, null, null, JobSource.APPLIED, null, null, LocalDate.of(2026, 3, 1));

        assertThatThrownBy(() -> jobs.requireOwned(app.getId(), userB)).isInstanceOf(ApiException.class);
    }
}
