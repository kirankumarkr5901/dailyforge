package com.dailyforge.points.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.UserDirectory;
import com.dailyforge.identity.domain.UserZone;
import com.dailyforge.points.repo.UserRolloverStateRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs hourly (spec §5.6). For each user whose local date has advanced since this job
 * last looked, every fully-closed day in between is settled exactly once, by handing it
 * to every registered {@link RolloverParticipant}.
 *
 * "Fully closed" matters on a job that only runs hourly on a possibly-sleeping free-tier
 * host (docs/DEPLOYMENT.md phase 1b): if the process was asleep for six hours, three
 * users' days might have turned over in that gap, and all three must be settled, not
 * just the most recent one. That is why this walks every date between the last
 * processed date and today, not just checks "has today changed".
 */
@Component
public class DailyRolloverJob {

    private static final Logger log = LoggerFactory.getLogger(DailyRolloverJob.class);

    private final UserDirectory users;
    private final DayService dayService;
    private final UserRolloverStateRepository states;
    private final List<RolloverParticipant> participants;

    public DailyRolloverJob(
            UserDirectory users,
            DayService dayService,
            UserRolloverStateRepository states,
            List<RolloverParticipant> participants) {
        this.users = users;
        this.dayService = dayService;
        this.states = states;
        this.participants = participants;
    }

    /** Five past the hour, so it does not race a request landing exactly on the hour. */
    @Scheduled(cron = "0 5 * * * *")
    public void run() {
        for (UserZone user : users.listActive()) {
            settleUser(user);
        }
    }

    @Transactional
    public void settleUser(UserZone user) {
        LocalDate today = dayService.today(user.zone());

        UserRolloverState state =
                states.findById(user.userId()).orElseGet(() -> UserRolloverState.fresh(user.userId()));

        LocalDate lastProcessed = state.getLastProcessedDate();
        // A user the job has never seen before has nothing to backfill: there is no
        // data from before they existed for a participant to act on, and looping over
        // days that predate the account is wasted work at best on every signup.
        LocalDate firstUnprocessed = lastProcessed == null ? today : lastProcessed.plusDays(1);

        // Every date from there up to (but not including) today is fully closed.
        for (LocalDate closed = firstUnprocessed; closed.isBefore(today); closed = closed.plusDays(1)) {
            for (RolloverParticipant participant : participants) {
                participant.onDayClosed(user.userId(), closed);
            }
        }

        if (lastProcessed == null || lastProcessed.isBefore(today.minusDays(1))) {
            state.advanceTo(today.minusDays(1));
            states.save(state);
            log.debug("Rollover settled for user {} through {}", user.userId(), today.minusDays(1));
        }
    }
}
