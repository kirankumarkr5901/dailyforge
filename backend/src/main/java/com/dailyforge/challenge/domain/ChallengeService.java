package com.dailyforge.challenge.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.habit.domain.Habit;
import com.dailyforge.habit.domain.HabitLog;
import com.dailyforge.habit.domain.HabitLogState;
import com.dailyforge.habit.domain.HabitService;
import com.dailyforge.habit.domain.HabitType;
import com.dailyforge.habit.domain.ScheduleDays;
import com.dailyforge.habit.repo.HabitLogRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Challenges: "Plank for 30 days straight".
 *
 * <p>Starting one creates a daily habit, which is the tick (and keeps paying its own
 * points, like any habit). The challenge tracks the current run of consecutive done
 * days; a closed day without a tick restarts it at day 1 (see {@link ChallengeRun}).
 * Settling happens on every read, so a miss while the app was closed is applied the next
 * time anyone looks. Reaching the length freezes the run as REACHED until it is claimed,
 * so a miss after reaching it never takes it away; claiming pays the bonus once.
 */
@Service
public class ChallengeService {

    static final String SOURCE_TYPE = "CHALLENGE";

    private final JdbcTemplate jdbc;
    private final HabitService habits;
    private final HabitLogRepository habitLogs;
    private final PointsService points;
    private final DayService dayService;
    private final IdentityService identity;
    private final jakarta.persistence.EntityManager entityManager;

    public ChallengeService(
            JdbcTemplate jdbc,
            HabitService habits,
            HabitLogRepository habitLogs,
            PointsService points,
            DayService dayService,
            IdentityService identity,
            jakarta.persistence.EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.habits = habits;
        this.habitLogs = habitLogs;
        this.points = points;
        this.dayService = dayService;
        this.identity = identity;
    }

    public enum Status {
        ACTIVE,
        REACHED,
        COMPLETED,
        ABANDONED
    }

    /** A challenge as the screens show it, already settled for today. */
    public record View(
            UUID id,
            UUID habitId,
            String title,
            String icon,
            int lengthDays,
            int bonusPoints,
            LocalDate startedOn,
            LocalDate runStart,
            int restarts,
            Status status,
            int dayNumber,
            int doneInARow,
            boolean doneToday) {}

    public record Claim(View challenge, PointsResult points) {}

    private record Row(
            UUID id,
            UUID habitId,
            String title,
            int lengthDays,
            int bonusPoints,
            LocalDate startedOn,
            LocalDate runStart,
            int restarts,
            Status status) {}

    public LocalDate today(UUID userId) {
        return dayService.today(zoneOf(userId));
    }

    @Transactional
    public View create(UUID userId, String title, String icon, int habitPoints, int lengthDays, int bonusPoints) {
        if (lengthDays < 2 || lengthDays > 365) {
            throw ApiException.outOfRange("lengthDays", "A challenge lasts between 2 and 365 days.");
        }
        if (bonusPoints < 0 || bonusPoints > 10000) {
            throw ApiException.outOfRange("bonusPoints", "The bonus must be between 0 and 10,000 points.");
        }
        LocalDate today = today(userId);
        Habit habit = habits.create(
                userId, title, icon, habitPoints, HabitType.NORMAL, 0, null, null, ScheduleDays.EVERY_DAY, today);
        // The habit is a JPA entity; write it now so the challenge row below can point at it.
        entityManager.flush();
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO challenge (id, user_id, habit_id, title, length_days, bonus_points, started_on, run_start, "
                        + "restarts, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'ACTIVE', ?, ?)",
                id,
                userId,
                habit.getId(),
                title,
                lengthDays,
                bonusPoints,
                Date.valueOf(today),
                Date.valueOf(today),
                now,
                now);
        return view(userId, requireRow(userId, id), today);
    }

    /** Active and reached challenges first, then finished ones, newest first. */
    @Transactional
    public List<View> list(UUID userId) {
        LocalDate today = today(userId);
        return rows(userId).stream().map(r -> view(userId, r, today)).toList();
    }

    @Transactional
    public Claim claim(UUID userId, UUID id) {
        LocalDate today = today(userId);
        View v = view(userId, requireRow(userId, id), today);
        if (v.status() != Status.REACHED) {
            throw new ApiException(
                    ErrorCode.CONFLICT, HttpStatus.CONFLICT, "This challenge isn't finished yet, or was already claimed.");
        }
        jdbc.update(
                "UPDATE challenge SET status = 'COMPLETED', claimed_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                id);
        PointsResult paid = v.bonusPoints() <= 0
                ? new PointsResult(List.of(), 0, points.snapshot(userId, zoneOf(userId)).total(), List.of())
                : points.award(new AwardCommand(
                        userId,
                        today,
                        PointsCategory.GOAL,
                        "CHALLENGE_COMPLETE",
                        v.bonusPoints(),
                        SOURCE_TYPE,
                        id,
                        "Challenge: " + v.title() + " (" + v.lengthDays() + " days)",
                        "challenge-complete:" + id));
        return new Claim(view(userId, requireRow(userId, id), today), paid);
    }

    /** Ends a challenge early. Its habit stays, with its history; points earned are kept. */
    @Transactional
    public View abandon(UUID userId, UUID id) {
        Row row = requireRow(userId, id);
        if (row.status() == Status.ACTIVE || row.status() == Status.REACHED) {
            jdbc.update("UPDATE challenge SET status = 'ABANDONED', updated_at = ? WHERE id = ?", Timestamp.from(Instant.now()), id);
        }
        return view(userId, requireRow(userId, id), today(userId));
    }

    /** Settles an active challenge against today (restart on a missed day, freeze on reaching it), then describes it. */
    private View view(UUID userId, Row row, LocalDate today) {
        Set<LocalDate> done = habitLogs.findAllByHabitIdAndOccurredOnGreaterThanEqualOrderByOccurredOnAsc(row.habitId(), row.startedOn())
                .stream()
                .filter(l -> l.getState() == HabitLogState.DONE)
                .map(HabitLog::getOccurredOn)
                .collect(Collectors.toSet());

        Row current = row;
        int dayNumber;
        int inARow;
        if (row.status() == Status.ACTIVE) {
            ChallengeRun.Settled s = ChallengeRun.settle(row.runStart(), today, done);
            if (s.restarted()) {
                jdbc.update(
                        "UPDATE challenge SET run_start = ?, restarts = restarts + 1, updated_at = ? WHERE id = ?",
                        Date.valueOf(s.runStart()),
                        Timestamp.from(Instant.now()),
                        row.id());
            }
            Status status = Status.ACTIVE;
            if (s.doneInARow() >= row.lengthDays()) {
                status = Status.REACHED;
                jdbc.update(
                        "UPDATE challenge SET status = 'REACHED', reached_on = ?, updated_at = ? WHERE id = ?",
                        Date.valueOf(today),
                        Timestamp.from(Instant.now()),
                        row.id());
            }
            current = new Row(row.id(), row.habitId(), row.title(), row.lengthDays(), row.bonusPoints(), row.startedOn(),
                    s.runStart(), row.restarts() + (s.restarted() ? 1 : 0), status);
            dayNumber = Math.min(s.dayNumber(), row.lengthDays());
            inARow = Math.min(s.doneInARow(), row.lengthDays());
        } else {
            // Reached, completed or abandoned: the run is frozen where it stopped.
            inARow = row.status() == Status.ABANDONED ? 0 : row.lengthDays();
            dayNumber = row.status() == Status.ABANDONED ? 0 : row.lengthDays();
        }
        String icon = habits.requireOwned(row.habitId(), userId).getIcon();
        return new View(
                current.id(),
                current.habitId(),
                current.title(),
                icon,
                current.lengthDays(),
                current.bonusPoints(),
                current.startedOn(),
                current.runStart(),
                current.restarts(),
                current.status(),
                dayNumber,
                inARow,
                done.contains(today));
    }

    private List<Row> rows(UUID userId) {
        return jdbc.query(
                "SELECT id, habit_id, title, length_days, bonus_points, started_on, run_start, restarts, status FROM challenge "
                        + "WHERE user_id = ? ORDER BY CASE status WHEN 'REACHED' THEN 0 WHEN 'ACTIVE' THEN 1 ELSE 2 END, created_at DESC",
                (rs, i) -> new Row(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getDate(6).toLocalDate(),
                        rs.getDate(7).toLocalDate(),
                        rs.getInt(8),
                        Status.valueOf(rs.getString(9))),
                userId);
    }

    private Row requireRow(UUID userId, UUID id) {
        return rows(userId).stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That challenge"));
    }

    private ZoneId zoneOf(UUID userId) {
        return dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
    }
}
