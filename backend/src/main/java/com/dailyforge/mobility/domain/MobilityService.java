package com.dailyforge.mobility.domain;

import com.dailyforge.common.error.ApiException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guided mobility flows. Playing a flow to its end records it for today and pays
 * {@code MOBILITY_FLOW} points once per flow per day. Finishing is the only way to
 * record one: the player calls this when the last move's countdown runs out, so there
 * is nothing to "unmark".
 */
@Service
public class MobilityService {

    static final String SOURCE_TYPE = "MOBILITY_LOG";
    static final String RULE_CODE = "MOBILITY_FLOW";

    private final JdbcTemplate jdbc;
    private final PointsService points;
    private final PointsRuleConfigService ruleConfigs;
    private final DayService dayService;
    private final IdentityService identity;

    public MobilityService(
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

    public record FlowState(MobilityCatalog.Flow flow, boolean doneToday, int timesDone) {}

    public record Board(LocalDate date, List<FlowState> flows) {}

    public record Completion(Board board, PointsResult points) {}

    @Transactional(readOnly = true)
    public Board board(UUID userId) {
        LocalDate today = today(userId);
        Map<String, Integer> totals = new HashMap<>();
        jdbc.query(
                "SELECT flow_code, COUNT(*) FROM mobility_log WHERE user_id = ? GROUP BY flow_code",
                rs -> {
                    totals.put(rs.getString(1), rs.getInt(2));
                },
                userId);
        List<String> doneToday = jdbc.queryForList(
                "SELECT flow_code FROM mobility_log WHERE user_id = ? AND occurred_on = ?", String.class, userId, today);
        return new Board(
                today,
                MobilityCatalog.FLOWS.stream()
                        .map(f -> new FlowState(f, doneToday.contains(f.code()), totals.getOrDefault(f.code(), 0)))
                        .toList());
    }

    /** Records a finished flow for today. Finishing the same flow twice in a day pays once. */
    @Transactional
    public Completion complete(UUID userId, String code) {
        MobilityCatalog.Flow flow = MobilityCatalog.find(code).orElseThrow(() -> ApiException.notFound("That flow"));
        LocalDate today = today(userId);
        boolean already = !jdbc.queryForList(
                        "SELECT id FROM mobility_log WHERE user_id = ? AND flow_code = ? AND occurred_on = ?",
                        UUID.class,
                        userId,
                        code,
                        today)
                .isEmpty();
        if (already) {
            return new Completion(board(userId), zeroResult(userId));
        }
        UUID logId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mobility_log (id, user_id, flow_code, occurred_on, created_at) VALUES (?, ?, ?, ?, ?)",
                logId,
                userId,
                code,
                today,
                Timestamp.from(Instant.now()));

        var config = ruleConfigs.getSystemDefault(RULE_CODE);
        int amount = config.enabled() ? config.getInt("points") : 0;
        PointsResult paid = amount <= 0
                ? zeroResult(userId)
                : points.award(new AwardCommand(
                        userId,
                        today,
                        PointsCategory.WORKOUT,
                        RULE_CODE,
                        amount,
                        SOURCE_TYPE,
                        logId,
                        "Mobility: " + flow.name(),
                        "mobility-log:" + logId));
        return new Completion(board(userId), paid);
    }

    private LocalDate today(UUID userId) {
        return dayService.today(zoneOf(userId));
    }

    private PointsResult zeroResult(UUID userId) {
        return new PointsResult(List.of(), 0, points.snapshot(userId, zoneOf(userId)).total(), List.of());
    }

    private ZoneId zoneOf(UUID userId) {
        return dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
    }
}
