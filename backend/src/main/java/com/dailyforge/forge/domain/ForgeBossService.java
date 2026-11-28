package com.dailyforge.forge.domain;

import static com.dailyforge.ai.GeminiClient.object;
import static com.dailyforge.ai.GeminiClient.string;

import com.dailyforge.ai.GeminiClient;
import com.dailyforge.forge.repo.ForgeQuestRepository;
import com.dailyforge.forge.repo.ForgeQuestTemplateRepository;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Forge 66 boss fights. Each Monday-to-Sunday week of a running program gets a boss
 * built from the weakest stat among that week's quest areas; finishing every quest in
 * its area that week beats it for {@code 200 + 100 x (level - 1)} XP. A boss left
 * standing when the week ends escapes and returns the next week one level stronger.
 * The AI names and describes the boss; without it a plain name stands in.
 */
@Service
public class ForgeBossService {

    private static final Logger log = LoggerFactory.getLogger(ForgeBossService.class);
    public static final int BASE_XP = 200;
    public static final int XP_PER_RETURN = 100;

    public enum Status { ACTIVE, DEFEATED, ESCAPED }

    public record Boss(
            UUID id,
            UUID userId,
            UUID programId,
            LocalDate weekStart,
            ForgeArea area,
            String name,
            String description,
            int level,
            int rewardXp,
            Status status,
            LocalDate defeatedOn) {}

    /** The week's boss with how far the fight has got. */
    public record Fight(Boss boss, int questsDone, int questsTotal) {}

    private static final Map<ForgeArea, String[]> FALLBACK = new EnumMap<>(Map.of(
            ForgeArea.FITNESS, new String[] {"The Couch Golem", "Heavy, slow and comfortable. It wins when you skip the work."},
            ForgeArea.HEALTH, new String[] {"The Midnight Snacker", "It feeds on late nights, skipped water and easy food."},
            ForgeArea.FOCUS, new String[] {"The Screen Wraith", "It lives in the scroll. Every ignored notification weakens it."},
            ForgeArea.MINDSET, new String[] {"The Fog of Doubt", "It whispers that today doesn't count. Prove it wrong."},
            ForgeArea.LEARNING, new String[] {"The Tomorrow Troll", "It guards every 'I'll start next week'."},
            ForgeArea.SOCIAL, new String[] {"The Silent Hermit", "It grows in the messages you never send."},
            ForgeArea.FINANCE, new String[] {"The Leaky Wallet", "It eats small spends you never notice."},
            ForgeArea.CAREER, new String[] {"The Comfort Plateau", "It keeps you busy so you never move up."},
            ForgeArea.RELATIONSHIPS, new String[] {"The Distance Shade", "It grows in the calls you put off."},
            ForgeArea.SOCIETY, new String[] {"The Bystander", "It whispers that someone else will help."}));

    static final RowMapper<Boss> ROW = (rs, i) -> new Boss(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getObject("program_id", UUID.class),
            rs.getDate("week_start").toLocalDate(),
            ForgeArea.valueOf(rs.getString("area")),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("level"),
            rs.getInt("reward_xp"),
            Status.valueOf(rs.getString("status")),
            rs.getDate("defeated_on") == null ? null : rs.getDate("defeated_on").toLocalDate());

    private final JdbcTemplate jdbc;
    private final ForgeQuestRepository quests;
    private final ForgeQuestTemplateRepository templates;
    private final PointsService points;
    private final GeminiClient gemini;

    public ForgeBossService(
            JdbcTemplate jdbc,
            ForgeQuestRepository quests,
            ForgeQuestTemplateRepository templates,
            PointsService points,
            GeminiClient gemini) {
        this.jdbc = jdbc;
        this.quests = quests;
        this.templates = templates;
        this.points = points;
        this.gemini = gemini;
    }

    public static LocalDate mondayOf(LocalDate d) {
        return d.minusDays(d.getDayOfWeek().getValue() - 1L);
    }

    public static int rewardFor(int level) {
        return BASE_XP + XP_PER_RETURN * (level - 1);
    }

    /**
     * Past weeks' bosses still standing escape; this week gets its boss (a returning one
     * first, else one from the weakest stat among this week's quest areas).
     */
    public void settle(UUID userId, ForgeProgram program, LocalDate today, Map<ForgeArea, Integer> stats) {
        LocalDate week = mondayOf(today);
        jdbc.update("UPDATE forge_boss SET status = 'ESCAPED' WHERE user_id = ? AND status = 'ACTIVE' AND week_start < ?",
                userId, Date.valueOf(week));
        if (find(program.getId(), week).isPresent()) {
            return;
        }
        List<ForgeQuest> weekQuests = questsOfWeek(program.getId(), week);
        if (weekQuests.isEmpty()) {
            return;
        }
        Map<String, ForgeQuestTemplate> byCode = templatesByCode();

        Optional<Boss> escaped = jdbc.query(
                        "SELECT * FROM forge_boss WHERE user_id = ? AND week_start = ? AND status = 'ESCAPED'",
                        ROW, userId, Date.valueOf(week.minusDays(7)))
                .stream()
                .findFirst();
        ForgeArea area;
        String name;
        String description;
        int level;
        if (escaped.isPresent()) {
            area = escaped.get().area();
            name = escaped.get().name();
            level = escaped.get().level() + 1;
            description = "It escaped last week and came back stronger. " + escaped.get().description();
        } else {
            var present = weekQuests.stream().map(q -> areaOf(q, byCode)).distinct().toList();
            area = present.stream().min(Comparator.comparingInt(a -> stats.getOrDefault(a, 0))).orElse(present.get(0));
            String[] named = name(area, weekQuests.stream().filter(q -> areaOf(q, byCode) == area).map(q -> titleOf(q, byCode)).toList());
            name = named[0];
            description = named[1];
            level = 1;
        }
        description = description.length() > 300 ? description.substring(0, 300) : description;
        jdbc.update(
                "INSERT INTO forge_boss (id, user_id, program_id, week_start, area, name, description, level, reward_xp, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                UUID.randomUUID(), userId, program.getId(), Date.valueOf(week), area.name(), name, description, level,
                rewardFor(level), Timestamp.from(Instant.now()));
    }

    /**
     * After a quest is marked or unmarked: the boss is beaten once every quest in its
     * area this week is done (for an AI program, once the whole week is planned), and
     * un-beaten if one is unmarked again the same day. Pays or takes back the points.
     */
    public PointsResult evaluate(UUID userId, ForgeProgram program, LocalDate today) {
        Boss boss = find(program.getId(), mondayOf(today)).orElse(null);
        if (boss == null || boss.status() == Status.ESCAPED) {
            return null;
        }
        Fight fight = fight(boss);
        boolean weekPlanned = !program.isAi()
                || (program.getPlannedThrough() != null && !program.getPlannedThrough().isBefore(
                        min(boss.weekStart().plusDays(6), program.getEndDate())));
        boolean beaten = fight.questsTotal() > 0 && fight.questsDone() == fight.questsTotal() && weekPlanned;
        if (beaten && boss.status() == Status.ACTIVE) {
            jdbc.update("UPDATE forge_boss SET status = 'DEFEATED', defeated_on = ? WHERE id = ?", Date.valueOf(today), boss.id());
        } else if (!beaten && boss.status() == Status.DEFEATED && today.equals(boss.defeatedOn())) {
            jdbc.update("UPDATE forge_boss SET status = 'ACTIVE', defeated_on = NULL WHERE id = ?", boss.id());
        } else {
            return null;
        }
        return points.reconcile(userId, new ReconcileScope.ForgeBoss(boss.id()));
    }

    public Optional<Fight> currentFight(UUID programId, LocalDate today) {
        return find(programId, mondayOf(today)).map(this::fight);
    }

    /** XP from beaten bosses, by area: added to the stats and the level. */
    public Map<ForgeArea, Integer> defeatedXpByArea(UUID userId) {
        Map<ForgeArea, Integer> xp = new EnumMap<>(ForgeArea.class);
        jdbc.query("SELECT area, reward_xp FROM forge_boss WHERE user_id = ? AND status = 'DEFEATED'",
                rs -> {
                    xp.merge(ForgeArea.valueOf(rs.getString(1)), rs.getInt(2), Integer::sum);
                }, userId);
        return xp;
    }

    public Optional<Boss> byId(UUID id) {
        return jdbc.query("SELECT * FROM forge_boss WHERE id = ?", ROW, id).stream().findFirst();
    }

    private Optional<Boss> find(UUID programId, LocalDate week) {
        return jdbc.query("SELECT * FROM forge_boss WHERE program_id = ? AND week_start = ?", ROW, programId, Date.valueOf(week))
                .stream()
                .findFirst();
    }

    private Fight fight(Boss boss) {
        Map<String, ForgeQuestTemplate> byCode = templatesByCode();
        List<ForgeQuest> inArea = questsOfWeek(boss.programId(), boss.weekStart()).stream()
                .filter(q -> areaOf(q, byCode) == boss.area())
                .toList();
        return new Fight(boss, (int) inArea.stream().filter(ForgeQuest::isDone).count(), inArea.size());
    }

    private List<ForgeQuest> questsOfWeek(UUID programId, LocalDate week) {
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> quests.findAllByProgramIdAndQuestDate(programId, week.plusDays(i)))
                .flatMap(List::stream)
                .toList();
    }

    /** The AI's name for the boss, from the area and this week's quests in it; a plain one without AI. */
    private String[] name(ForgeArea area, List<String> questTitles) {
        String[] fallback = FALLBACK.get(area);
        if (!gemini.enabled()) {
            return fallback;
        }
        try {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("name", string());
            props.put("description", string());
            var a = gemini.generate(
                    "Invent a playful weekly 'boss' for a habit-building game. It embodies the obstacle behind this area: "
                            + area + ", whose quests this week are: " + String.join("; ", questTitles) + ". name: 2-4 words, "
                            + "\"The ...\" (original, not from any existing game, film or book). description: one short, witty "
                            + "sentence on how it fights and how finishing those quests beats it. Keep it friendly.",
                    null,
                    object(props));
            String name = a.path("name").asString("").strip();
            String description = a.path("description").asString("").strip();
            if (name.isEmpty() || name.length() > 80 || description.isEmpty()) {
                return fallback;
            }
            return new String[] {name, description};
        } catch (RuntimeException e) {
            log.warn("Boss naming failed, using the plain name: {}", e.toString());
            return fallback;
        }
    }

    private Map<String, ForgeQuestTemplate> templatesByCode() {
        return templates.findAll().stream().collect(Collectors.toMap(ForgeQuestTemplate::getCode, Function.identity()));
    }

    private static ForgeArea areaOf(ForgeQuest q, Map<String, ForgeQuestTemplate> byCode) {
        if (q.isCustom()) {
            return q.getArea();
        }
        ForgeQuestTemplate t = byCode.get(q.getTemplateCode());
        return t == null ? ForgeArea.MINDSET : t.getArea();
    }

    private static String titleOf(ForgeQuest q, Map<String, ForgeQuestTemplate> byCode) {
        if (q.isCustom()) {
            return q.getTitle();
        }
        ForgeQuestTemplate t = byCode.get(q.getTemplateCode());
        return t == null ? "Quest" : t.getTitle();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
