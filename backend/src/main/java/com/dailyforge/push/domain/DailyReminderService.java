package com.dailyforge.push.domain;

import com.dailyforge.challenge.domain.ChallengeService;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.domain.HabitBoardService;
import com.dailyforge.habit.domain.HabitDayState;
import com.dailyforge.nutrition.domain.ProteinService;
import com.dailyforge.nutrition.repo.NutritionSettingsRepository;
import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One daily nudge at the user's chosen time, only if something is still open:
 * "Still to do: Plank (day 12), 2 habits, 60 g protein".
 *
 * <p>Built around a metered database. Who wants a reminder, and when, is held in memory
 * and refreshed hourly (the rollover job already wakes the database then); the minute
 * tick compares clocks in memory and touches the database only when a reminder is
 * actually due. A reminder missed while the server slept is still sent within three
 * hours of its time, and dropped after that rather than arriving at a strange hour.
 */
@Service
public class DailyReminderService {

    private static final Logger log = LoggerFactory.getLogger(DailyReminderService.class);
    private static final Duration GRACE = Duration.ofHours(3);

    private final JdbcTemplate jdbc;
    private final PushSender sender;
    private final HabitBoardService habitBoard;
    private final ChallengeService challenges;
    private final ProteinService protein;
    private final NutritionSettingsRepository nutritionSettings;
    private final DayService dayService;
    private final Clock clock;
    private final com.dailyforge.identity.domain.IdentityService identity;
    private final com.dailyforge.insight.domain.WeeklyCoachService weeklyCoach;

    private record Due(ZoneId zone, LocalTime time) {}

    private final Map<UUID, Due> schedule = new ConcurrentHashMap<>();
    /** Users already handled for a given local date, so the tick stays off the database. */
    private final Map<UUID, LocalDate> handled = new ConcurrentHashMap<>();

    public DailyReminderService(
            JdbcTemplate jdbc,
            PushSender sender,
            HabitBoardService habitBoard,
            ChallengeService challenges,
            ProteinService protein,
            NutritionSettingsRepository nutritionSettings,
            DayService dayService,
            Clock clock,
            com.dailyforge.identity.domain.IdentityService identity,
            com.dailyforge.insight.domain.WeeklyCoachService weeklyCoach) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.habitBoard = habitBoard;
        this.challenges = challenges;
        this.protein = protein;
        this.nutritionSettings = nutritionSettings;
        this.dayService = dayService;
        this.clock = clock;
        this.identity = identity;
        this.weeklyCoach = weeklyCoach;
    }

    // ------------------------------------------------------------------ settings

    public LocalTime reminderTime(UUID userId) {
        List<Time> times = jdbc.queryForList("SELECT reminder_time FROM user_settings WHERE user_id = ?", Time.class, userId);
        return times.isEmpty() || times.get(0) == null ? null : times.get(0).toLocalTime();
    }

    /** Sets or clears (null) the time, and updates the in-memory schedule straight away. */
    public void setReminderTime(UUID userId, LocalTime time) {
        jdbc.update("UPDATE user_settings SET reminder_time = ? WHERE user_id = ?", time == null ? null : Time.valueOf(time), userId);
        refresh(userId);
    }

    // ------------------------------------------------------------------ schedule

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 7 * * * *")
    public void refreshAll() {
        if (!sender.enabled()) {
            schedule.clear();
            return;
        }
        Map<UUID, Due> fresh = new ConcurrentHashMap<>();
        jdbc.query(
                "SELECT us.user_id, us.time_zone, us.reminder_time FROM user_settings us WHERE us.reminder_time IS NOT NULL "
                        + "AND EXISTS (SELECT 1 FROM push_subscription ps WHERE ps.user_id = us.user_id)",
                rs -> {
                    fresh.put(
                            rs.getObject(1, UUID.class),
                            new Due(dayService.zoneOf(rs.getString(2)), rs.getTime(3).toLocalTime()));
                });
        schedule.keySet().retainAll(fresh.keySet());
        schedule.putAll(fresh);
    }

    /** One user's entry, after they subscribe, unsubscribe or change the time. */
    public void refresh(UUID userId) {
        schedule.remove(userId);
        if (!sender.enabled()) {
            return;
        }
        jdbc.query(
                "SELECT us.time_zone, us.reminder_time FROM user_settings us WHERE us.user_id = ? AND us.reminder_time IS NOT NULL "
                        + "AND EXISTS (SELECT 1 FROM push_subscription ps WHERE ps.user_id = us.user_id)",
                rs -> {
                    schedule.put(userId, new Due(dayService.zoneOf(rs.getString(1)), rs.getTime(2).toLocalTime()));
                },
                userId);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void tick() {
        for (Map.Entry<UUID, Due> e : schedule.entrySet()) {
            UUID userId = e.getKey();
            ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(e.getValue().zone());
            LocalDate today = now.toLocalDate();
            ZonedDateTime due = today.atTime(e.getValue().time()).atZone(e.getValue().zone());
            if (now.isBefore(due) || today.equals(handled.get(userId))) {
                continue;
            }
            handled.put(userId, today);
            if (now.isAfter(due.plus(GRACE))) {
                continue; // too late to be useful; tomorrow's will go out on time
            }
            try {
                sendIfOpen(userId, today);
            } catch (Exception ex) {
                log.warn("Daily reminder for {} failed: {}", userId, ex.toString());
            }
            try {
                sendWeeklyReview(userId, today);
            } catch (Exception ex) {
                log.warn("Weekly review for {} failed: {}", userId, ex.toString());
            }
        }
    }

    /** Claims the day (so a restart never sends twice), then sends only if something is open. */
    @Transactional
    public boolean sendIfOpen(UUID userId, LocalDate today) {
        try {
            jdbc.update("INSERT INTO reminder_sent (user_id, sent_on) VALUES (?, ?)", userId, Date.valueOf(today));
        } catch (DuplicateKeyException already) {
            return false;
        }
        String body = openItems(userId, today);
        if (body == null) {
            return false;
        }
        return sender.send(userId, new PushSender.Message("DailyForge", body, "dailyforge-daily-reminder", "/home"), 3600) > 0;
    }

    /** On Sundays, at the reminder time: the AI coach's review of the week, once. */
    public boolean sendWeeklyReview(UUID userId, LocalDate today) {
        var review = weeklyCoach.forSundayPush(userId, today);
        if (review == null) {
            return false;
        }
        String body = review.summary().length() > 160 ? review.summary().substring(0, 157) + "..." : review.summary();
        return sender.send(userId, new PushSender.Message("Your week", body, "dailyforge-weekly-review", "/home"), 6 * 3600) > 0;
    }

    /** "Still to do: Plank (day 12), 2 habits, 60 g protein", or null when nothing is open. */
    public String openItems(UUID userId, LocalDate today) {
        List<String> parts = new ArrayList<>();

        var running = challenges.list(userId).stream()
                .filter(c -> c.status() == ChallengeService.Status.ACTIVE && !c.doneToday())
                .toList();
        Set<UUID> challengeHabits = running.stream().map(ChallengeService.View::habitId).collect(Collectors.toSet());
        running.forEach(c -> parts.add(c.title() + " (day " + c.dayNumber() + ")"));

        long habitsOpen = habitBoard.board(userId, today).habits().stream()
                .filter(h -> h.state() == HabitDayState.PENDING && !challengeHabits.contains(h.id()))
                .count();
        if (habitsOpen > 0) {
            parts.add(habitsOpen + (habitsOpen == 1 ? " habit" : " habits"));
        }

        if (identity.requireSettings(userId).isFeatureEnabled("NUTRITION") && nutritionSettings.existsById(userId)) {
            var day = protein.day(userId, today);
            int left = (int) Math.ceil(day.targetG() - day.totalG());
            if (left > 0) {
                parts.add(left + " g protein");
            }
            int waterLeft = day.waterTargetMl() - day.waterMl();
            if (waterLeft > 0) {
                parts.add(String.format(java.util.Locale.ROOT, "%.1f L water", waterLeft / 1000.0));
            }
        }
        return parts.isEmpty() ? null : "Still to do: " + String.join(", ", parts);
    }
}
