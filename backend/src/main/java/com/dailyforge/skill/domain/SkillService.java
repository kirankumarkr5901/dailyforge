package com.dailyforge.skill.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.AwardCommand;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsRuleConfigService;
import com.dailyforge.points.domain.PointsService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill work: which level each skill is at, and which days its block was done.
 *
 * <p>Marking a block done pays {@code SKILL_BLOCK} points once per skill per day, as a
 * source-tracked award on that day's log row; unmarking deletes the row and reverses
 * exactly that award. Changing level is progress, not structure, so it needs no
 * "Enable edit". Two small tables, so plain JDBC.
 */
@Service
public class SkillService {

    static final String SOURCE_TYPE = "SKILL_LOG";
    static final String RULE_CODE = "SKILL_BLOCK";

    private final JdbcTemplate jdbc;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public SkillService(
            JdbcTemplate jdbc,
            PointsService points,
            PointsRuleConfigService ruleConfigs,
            DayService dayService,
            IdentityService identity) {
        this.jdbc = jdbc;
        this.points = points;
        this.ruleConfigs = ruleConfigs;
        this.dayService = dayService;
        this.identity = identity;
    }

    /** One skill as the screen shows it: the catalogue entry plus this user's state on {@code date}. */
    public record SkillState(SkillCatalog.Skill skill, int level, boolean doneOnDate, int daysDone) {}

    public record Board(LocalDate date, List<SkillState> skills) {}

    public record Write(Board board, PointsResult points) {}

    public LocalDate today(UUID userId) {
        return dayService.today(zoneOf(userId));
    }

    @Transactional(readOnly = true)
    public Board board(UUID userId, LocalDate date) {
        Map<String, Integer> levels = new HashMap<>();
        jdbc.query(
                "SELECT skill_code, level FROM skill_progress WHERE user_id = ?",
                rs -> {
                    levels.put(rs.getString(1), rs.getInt(2));
                },
                userId);
        Map<String, Integer> totals = new HashMap<>();
        jdbc.query(
                "SELECT skill_code, COUNT(*) FROM skill_log WHERE user_id = ? GROUP BY skill_code",
                rs -> {
                    totals.put(rs.getString(1), rs.getInt(2));
                },
                userId);
        List<String> doneToday = jdbc.queryForList(
                "SELECT skill_code FROM skill_log WHERE user_id = ? AND occurred_on = ?", String.class, userId, date);
        List<SkillState> skills = SkillCatalog.SKILLS.stream()
                .map(s -> new SkillState(
                        s, levels.getOrDefault(s.code(), 1), doneToday.contains(s.code()), totals.getOrDefault(s.code(), 0)))
                .toList();
        return new Board(date, skills);
    }

    @Transactional
    public Board setLevel(UUID userId, String code, int level) {
        SkillCatalog.Skill skill = requireSkill(code);
        if (level < 1 || level > skill.levels().size()) {
            throw ApiException.outOfRange("level", "Pick a level between 1 and " + skill.levels().size() + ".");
        }
        jdbc.update("DELETE FROM skill_progress WHERE user_id = ? AND skill_code = ?", userId, code);
        jdbc.update(
                "INSERT INTO skill_progress (user_id, skill_code, level, updated_at) VALUES (?, ?, ?, ?)",
                userId,
                code,
                level,
                Timestamp.from(Instant.now()));
        return board(userId, today(userId));
    }

    /** Marks the skill block done on {@code date}. Doing it twice is harmless: one row, one award. */
    @Transactional
    public Write markDone(UUID userId, String code, LocalDate date) {
        SkillCatalog.Skill skill = requireSkill(code);
        requireEditableDate(userId, date);
        UUID existing = logIdFor(userId, code, date);
        if (existing != null) {
            return new Write(board(userId, date), zeroResult(userId));
        }
        int level = board(userId, date).skills().stream()
                .filter(s -> s.skill().code().equals(code))
                .findFirst()
                .map(SkillState::level)
                .orElse(1);
        UUID logId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO skill_log (id, user_id, skill_code, occurred_on, level, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                logId,
                userId,
                code,
                date,
                level,
                Timestamp.from(Instant.now()));

        var config = ruleConfigs.getSystemDefault(RULE_CODE);
        int amount = config.enabled() ? config.getInt("points") : 0;
        PointsResult paid = amount <= 0
                ? zeroResult(userId)
                : points.award(new AwardCommand(
                        userId,
                        date,
                        PointsCategory.WORKOUT,
                        RULE_CODE,
                        amount,
                        SOURCE_TYPE,
                        logId,
                        "Skill block: " + skill.name(),
                        "skill-log:" + logId));
        return new Write(board(userId, date), paid);
    }

    /** Takes the mark back, and with it the points it paid. */
    @Transactional
    public Write markNotDone(UUID userId, String code, LocalDate date) {
        requireSkill(code);
        requireEditableDate(userId, date);
        UUID logId = logIdFor(userId, code, date);
        if (logId == null) {
            return new Write(board(userId, date), zeroResult(userId));
        }
        int before = points.snapshot(userId, zoneOf(userId)).total();
        points.reverseBySource(SOURCE_TYPE, logId, "Skill block unmarked");
        jdbc.update("DELETE FROM skill_log WHERE id = ?", logId);
        int after = points.snapshot(userId, zoneOf(userId)).total();
        return new Write(board(userId, date), new PointsResult(List.of(), after - before, after, List.of()));
    }

    private UUID logIdFor(UUID userId, String code, LocalDate date) {
        List<UUID> ids = jdbc.queryForList(
                "SELECT id FROM skill_log WHERE user_id = ? AND skill_code = ? AND occurred_on = ?",
                UUID.class,
                userId,
                code,
                date);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private static SkillCatalog.Skill requireSkill(String code) {
        return SkillCatalog.find(code).orElseThrow(() -> ApiException.notFound("That skill"));
    }

    private PointsResult zeroResult(UUID userId) {
        return new PointsResult(List.of(), 0, points.snapshot(userId, zoneOf(userId)).total(), List.of());
    }

    private ZoneId zoneOf(UUID userId) {
        return dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
    }

    private void requireEditableDate(UUID userId, LocalDate date) {
        int generalDays = ruleConfigs.getSystemDefault("EDIT_WINDOW").getInt("generalDays");
        if (!dayService.isWithinEditWindow(date, zoneOf(userId), generalDays)) {
            throw new ApiException(ErrorCode.ENTRY_LOCKED, HttpStatus.FORBIDDEN, "This day can no longer be changed.");
        }
    }
}
