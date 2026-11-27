package com.dailyforge.forge.domain;

import com.dailyforge.common.error.ApiException;
import com.dailyforge.common.error.ErrorCode;
import com.dailyforge.common.time.DayService;
import com.dailyforge.forge.domain.ForgeProgressCalculator.Progress;
import com.dailyforge.forge.domain.ForgeProgressCalculator.QuestFact;
import com.dailyforge.forge.repo.ForgeProgramRepository;
import com.dailyforge.forge.repo.ForgeQuestRepository;
import com.dailyforge.forge.repo.ForgeQuestTemplateRepository;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.domain.PointsResult;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.domain.ReconcileScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Forge 66: start a program from the questionnaire, mark today's quests, read progress.
 *
 * Kept apart from the rest of the app on purpose (owner request): nothing here reads
 * habits, workouts or runs, and the only thing it writes outside its own tables is the
 * points a completed quest pays, through {@link PointsService}.
 */
@Service
public class ForgeService {

    private final ForgeProgramRepository programs;
    private final ForgeQuestRepository quests;
    private final ForgeQuestTemplateRepository templates;
    private final ForgeSettingsProvider settingsProvider;
    private final PointsService points;
    private final DayService dayService;
    private final IdentityService identity;
    private final ObjectMapper objectMapper;
    private final ForgeAiPlanner ai;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ForgeService.class);

    public ForgeService(
            ForgeProgramRepository programs,
            ForgeQuestRepository quests,
            ForgeQuestTemplateRepository templates,
            ForgeSettingsProvider settingsProvider,
            PointsService points,
            DayService dayService,
            IdentityService identity,
            ObjectMapper objectMapper,
            ForgeAiPlanner ai) {
        this.programs = programs;
        this.quests = quests;
        this.templates = templates;
        this.settingsProvider = settingsProvider;
        this.points = points;
        this.dayService = dayService;
        this.identity = identity;
        this.objectMapper = objectMapper;
        this.ai = ai;
    }

    /** Everything the Forge 66 screen shows, in one read. */
    public record Overview(
            LocalDate today,
            ForgeSettings settings,
            Optional<ForgeProgram> activeProgram,
            Optional<ForgeProgram> lastFinishedProgram,
            List<ForgeQuest> todaysQuests,
            Map<String, ForgeQuestTemplate> templatesByCode,
            Progress progress) {}

    /** A quest mark or unmark: the new state, plus what it changed. */
    public record QuestChange(Overview overview, PointsResult points, int levelBefore, boolean dayCompleted) {}

    @Transactional
    public Overview overview(UUID userId) {
        LocalDate today = todayFor(userId);
        settle(userId, today);
        return buildOverview(userId, today);
    }

    @Transactional
    public Overview start(UUID userId, Map<String, List<String>> answers, ForgeMode mode) {
        if (mode == null) {
            throw ApiException.outOfRange("mode", "Choose Normal or Hard mode.");
        }
        LocalDate today = todayFor(userId);
        settle(userId, today);
        if (programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE).isPresent()) {
            throw alreadyRunning();
        }

        ForgeSettings settings = settingsProvider.current();
        Map<ForgeArea, Integer> scores = ForgeQuestionnaire.score(answers);
        List<ForgeArea> focus = ForgeQuestionnaire.focusAreas(scores, settings.focusAreas());

        ForgeProgram program =
                ForgeProgram.start(userId, today, settings.lengthDays(), mode, focus, objectMapper.writeValueAsString(answers));
        try {
            programs.saveAndFlush(program);
        } catch (DataIntegrityViolationException raced) {
            // A second start from another device landed first; the unique active marker
            // is what caught it.
            throw alreadyRunning();
        }

        planQuests(program, today, settings, scores);

        return buildOverview(userId, today);
    }

    /**
     * Starts a program from the AI interview: the AI writes the person's profile, focus
     * areas, phases and first week; later weeks are written as the program runs.
     */
    @Transactional
    public Overview startAi(UUID userId, List<ForgeAiPlanner.Turn> turns, ForgeMode mode) {
        if (mode == null) {
            throw ApiException.outOfRange("mode", "Choose Normal or Hard mode.");
        }
        LocalDate today = todayFor(userId);
        settle(userId, today);
        if (programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE).isPresent()) {
            throw alreadyRunning();
        }
        ForgeSettings settings = settingsProvider.current();
        ForgeAiPlanner.Plan plan = ai.buildPlan(userId, turns, settings, mode);

        ForgeProgram program = ForgeProgram.start(userId, today, settings.lengthDays(), mode, plan.focusAreas(), "{}");
        program.makeAi(plan.profile().isBlank() ? "(no profile)" : plan.profile(), plan.outline());
        try {
            // A new entity with its own id is merged: carry on with the managed copy.
            program = programs.saveAndFlush(program);
        } catch (DataIntegrityViolationException raced) {
            throw alreadyRunning();
        }
        saveDays(program, today, 1, Math.min(ForgeAiPlanner.WEEK, settings.lengthDays()), plan.days(), settings);
        return buildOverview(userId, today);
    }

    /**
     * An AI program writes its next week before the planned days run out, re-tuned from
     * what was done. If the AI can't be reached, the last week's quests repeat, so a day
     * is never left empty.
     */
    private void ensurePlanned(UUID userId, LocalDate today) {
        ForgeProgram program = programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE)
                .filter(ForgeProgram::isAi)
                .orElse(null);
        if (program == null) {
            return;
        }
        LocalDate planned = program.getPlannedThrough();
        LocalDate needed = today.plusDays(1).isAfter(program.getEndDate()) ? program.getEndDate() : today.plusDays(1);
        if (planned != null && !planned.isBefore(needed)) {
            return;
        }
        LocalDate from = planned == null || planned.isBefore(today) ? today : planned.plusDays(1);
        if (from.isBefore(program.getStartDate())) {
            from = program.getStartDate();
        }
        int firstDay = (int) (from.toEpochDay() - program.getStartDate().toEpochDay()) + 1;
        int days = (int) Math.min(ForgeAiPlanner.WEEK, program.getEndDate().toEpochDay() - from.toEpochDay() + 1);
        if (days <= 0) {
            return;
        }
        ForgeSettings settings = settingsProvider.current();

        List<ForgeQuest> recent = quests.findAllByUserIdOrderByQuestDateAsc(userId).stream()
                .filter(q -> !q.getQuestDate().isBefore(today.minusDays(14)) && q.getQuestDate().isBefore(today))
                .toList();
        Map<String, ForgeQuestTemplate> byCode = templatesByCode();
        List<String> history = recent.stream()
                .map(q -> q.getQuestDate() + " " + titleOf(byCode, q) + ": " + (q.isDone() ? "done" : "missed"))
                .toList();

        List<ForgeAiPlanner.DayPlan> plan;
        try {
            plan = ai.nextWeek(userId, program, firstDay, days, history.isEmpty() ? List.of("(nothing yet)") : history, settings);
        } catch (RuntimeException e) {
            log.warn("Forge week plan for {} failed, repeating last week: {}", userId, e.toString());
            plan = List.of();
        }
        if (plan.isEmpty()) {
            plan = repeatLastWeek(userId, from, days, byCode);
        }
        saveDays(program, from, firstDay, days, plan, settings);
    }

    /** The fallback: each new day gets the quests of the same weekday a week before. */
    private List<ForgeAiPlanner.DayPlan> repeatLastWeek(
            UUID userId, LocalDate from, int days, Map<String, ForgeQuestTemplate> byCode) {
        Map<LocalDate, List<ForgeQuest>> byDay = quests.findAllByUserIdOrderByQuestDateAsc(userId).stream()
                .filter(q -> q.isCustom())
                .collect(Collectors.groupingBy(ForgeQuest::getQuestDate));
        List<ForgeAiPlanner.DayPlan> out = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate source = from.plusDays(i).minusDays(ForgeAiPlanner.WEEK);
            for (int back = 0; back < 4 && !byDay.containsKey(source); back++) {
                source = source.minusDays(ForgeAiPlanner.WEEK);
            }
            List<ForgeQuest> same = byDay.getOrDefault(source, List.of());
            if (!same.isEmpty()) {
                out.add(new ForgeAiPlanner.DayPlan(i, same.stream()
                        .map(q -> new ForgeAiPlanner.QuestDraft(q.getTitle(), q.getDescription(), q.getArea(), q.getTier()))
                        .toList()));
            }
        }
        return out;
    }

    private void saveDays(
            ForgeProgram program, LocalDate from, int firstDay, int days, List<ForgeAiPlanner.DayPlan> plan, ForgeSettings settings) {
        List<ForgeQuest> rows = new java.util.ArrayList<>();
        for (ForgeAiPlanner.DayPlan day : plan) {
            if (day.offset() < 0 || day.offset() >= days) {
                continue;
            }
            for (ForgeAiPlanner.QuestDraft q : day.quests()) {
                rows.add(ForgeQuest.custom(
                        program.getId(), program.getUserId(), from.plusDays(day.offset()), firstDay + day.offset(),
                        q.title(), q.description(), q.area(), q.tier(), ForgeAiPlanner.xpFor(q.tier(), program.getMode(), settings)));
            }
        }
        quests.saveAll(rows);
        program.setPlannedThrough(from.plusDays(days - 1L));
        programs.saveAndFlush(program);
    }

    private static String titleOf(Map<String, ForgeQuestTemplate> byCode, ForgeQuest q) {
        if (q.isCustom()) {
            return q.getTitle();
        }
        ForgeQuestTemplate t = byCode.get(q.getTemplateCode());
        return t == null ? "Quest" : t.getTitle();
    }

    /** Ends the running program from today. Closed days keep their history and XP. */
    @Transactional
    public Overview abandon(UUID userId) {
        LocalDate today = todayFor(userId);
        settle(userId, today);
        ForgeProgram program =
                programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE)
                        .orElseThrow(() -> ApiException.notFound("A running program"));
        program.abandon(today);
        programs.save(program);
        return buildOverview(userId, today);
    }

    @Transactional
    public QuestChange markDone(UUID userId, UUID questId) {
        return change(userId, questId, true);
    }

    @Transactional
    public QuestChange markNotDone(UUID userId, UUID questId) {
        return change(userId, questId, false);
    }

    private QuestChange change(UUID userId, UUID questId, boolean done) {
        LocalDate today = todayFor(userId);
        settle(userId, today);

        ForgeQuest quest = quests.findByIdAndUserId(questId, userId).orElseThrow(() -> ApiException.notFound("That quest"));
        ForgeProgram program = programs.findById(quest.getProgramId()).orElseThrow(() -> ApiException.notFound("That quest"));
        if (!program.isActive() || !quest.getQuestDate().equals(today)) {
            throw new ApiException(ErrorCode.ENTRY_LOCKED, HttpStatus.FORBIDDEN, "Only today's quests can be changed.");
        }

        ForgeSettings settings = settingsProvider.current();
        Progress before = progressFor(userId, today, settings);
        boolean dayWasComplete = isDayComplete(program.getId(), today);

        // Already in the requested state: a replay, not an error — the reconcile below
        // then finds nothing to change and returns a zero delta.
        if (done && !quest.isDone()) {
            quest.markDone(Instant.now());
        } else if (!done && quest.isDone()) {
            quest.markNotDone();
        }
        quests.saveAndFlush(quest);

        PointsResult paid = points.reconcile(userId, new ReconcileScope.ForgeQuest(quest.getId()));
        boolean dayCompleted = !dayWasComplete && isDayComplete(program.getId(), today);

        return new QuestChange(buildOverview(userId, today), paid, before.level().level(), dayCompleted);
    }

    private boolean isDayComplete(UUID programId, LocalDate day) {
        List<ForgeQuest> dayQuests = quests.findAllByProgramIdAndQuestDate(programId, day);
        return !dayQuests.isEmpty() && dayQuests.stream().allMatch(ForgeQuest::isDone);
    }

    /**
     * Hard mode: the first closed day that was missed or left partial ends the run, and a
     * new run starts at day 1 today, with the same focus areas and a freshly shuffled
     * plan. Lazy, like {@link #closeIfFinished}: a day that failed while the app was
     * closed is settled on the next read. Quests already done keep their XP and points,
     * because their rows (and the ledger entries they paid) are left as they are.
     */
    private void restartIfHardModeFailed(UUID userId, LocalDate today) {
        ForgeProgram program = programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE)
                .filter(p -> p.getMode() == ForgeMode.HARD)
                .orElse(null);
        if (program == null) {
            return;
        }
        LocalDate lastClosed = today.minusDays(1).isBefore(program.getEndDate()) ? today.minusDays(1) : program.getEndDate();
        Map<LocalDate, List<ForgeQuest>> byDay = quests.findAllByUserIdOrderByQuestDateAsc(userId).stream()
                .filter(q -> q.getProgramId().equals(program.getId()))
                .collect(Collectors.groupingBy(ForgeQuest::getQuestDate));
        LocalDate failedDay = null;
        for (LocalDate d = program.getStartDate(); !d.isAfter(lastClosed); d = d.plusDays(1)) {
            List<ForgeQuest> dayQuests = byDay.getOrDefault(d, List.of());
            if (!dayQuests.isEmpty() && !dayQuests.stream().allMatch(ForgeQuest::isDone)) {
                failedDay = d;
                break;
            }
        }
        if (failedDay == null) {
            return;
        }

        program.restartAfter(failedDay);
        programs.saveAndFlush(program);
        // The old run's days after the failure will never be played. Only quests still
        // undone are removed, so nothing that earned XP or points is ever deleted.
        LocalDate ended = failedDay;
        quests.deleteAll(byDay.entrySet().stream()
                .filter(e -> e.getKey().isAfter(ended))
                .flatMap(e -> e.getValue().stream())
                .filter(q -> !q.isDone())
                .toList());
        quests.flush();

        Map<String, List<String>> answers = readAnswers(program.getAnswersJson());
        ForgeSettings settings = settingsProvider.current();
        Map<ForgeArea, Integer> scores = ForgeQuestionnaire.score(answers);
        ForgeProgram next = ForgeProgram.start(
                userId, today, settings.lengthDays(), ForgeMode.HARD, program.getFocusAreas(), program.getAnswersJson());
        if (program.isAi()) {
            // The same person and plan; the next week is written (re-tuned) by ensurePlanned.
            next.makeAi(program.getAiProfile(), program.getAiOutline());
            programs.saveAndFlush(next);
            return;
        }
        programs.saveAndFlush(next);
        planQuests(next, today, settings, scores);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> readAnswers(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    /** Every quest of a new run, generated from a seed of its own id so each run is shuffled anew. */
    private void planQuests(ForgeProgram program, LocalDate startDate, ForgeSettings settings, Map<ForgeArea, Integer> scores) {
        long seed = program.getId().getMostSignificantBits() ^ program.getId().getLeastSignificantBits();
        List<ForgeQuest> planned =
                PlanGenerator.generate(
                                seed, startDate, settings, program.getMode(), program.getFocusAreas(), scores, templates.findAll())
                        .stream()
                        .map(p -> ForgeQuest.planned(program.getId(), program.getUserId(), p.date(), p.dayNumber(), p.templateCode(), p.xp()))
                        .toList();
        quests.saveAll(planned);
    }

    /** Settles everything that happened while nobody was looking: a Hard mode restart, then a finished run. */
    private void settle(UUID userId, LocalDate today) {
        restartIfHardModeFailed(userId, today);
        closeIfFinished(userId, today);
        ensurePlanned(userId, today);
    }

    /** Marks a program whose last day has passed as completed. Lazy — no job needed. */
    private void closeIfFinished(UUID userId, LocalDate today) {
        programs.findFirstByUserIdAndStatus(userId, ForgeProgramStatus.ACTIVE)
                .filter(p -> today.isAfter(p.getEndDate()))
                .ifPresent(
                        p -> {
                            p.complete();
                            programs.saveAndFlush(p);
                        });
    }

    private Progress progressFor(UUID userId, LocalDate today, ForgeSettings settings) {
        Map<String, ForgeQuestTemplate> byCode = templatesByCode();
        List<ForgeProgram> all = programs.findAllByUserIdOrderByStartDateAscCreatedAtAsc(userId);
        List<QuestFact> facts =
                quests.findAllByUserIdOrderByQuestDateAsc(userId).stream()
                        .map(q -> new QuestFact(q.getProgramId(), q.getQuestDate(), areaOf(byCode, q), q.getXp(), q.isDone()))
                        .toList();
        return ForgeProgressCalculator.compute(all, facts, today, settings);
    }

    private Overview buildOverview(UUID userId, LocalDate today) {
        ForgeSettings settings = settingsProvider.current();
        List<ForgeProgram> all = programs.findAllByUserIdOrderByStartDateAscCreatedAtAsc(userId);
        Optional<ForgeProgram> active = all.stream().filter(ForgeProgram::isActive).findFirst();
        Optional<ForgeProgram> lastFinished =
                all.stream()
                        .filter(p -> !p.isActive())
                        .max(Comparator.comparing(ForgeProgram::getStartDate).thenComparing(ForgeProgram::getCreatedAt));

        List<ForgeQuest> todays =
                active.map(p -> quests.findAllByProgramIdAndQuestDate(p.getId(), today)).orElse(List.of());

        return new Overview(
                today, settings, active, lastFinished, todays, templatesByCode(), progressFor(userId, today, settings));
    }

    private Map<String, ForgeQuestTemplate> templatesByCode() {
        return templates.findAll().stream().collect(Collectors.toMap(ForgeQuestTemplate::getCode, Function.identity()));
    }

    private static ForgeArea areaOf(Map<String, ForgeQuestTemplate> byCode, ForgeQuest quest) {
        if (quest.isCustom()) {
            return quest.getArea();
        }
        ForgeQuestTemplate template = byCode.get(quest.getTemplateCode());
        if (template == null) {
            throw new IllegalStateException("No quest template '" + quest.getTemplateCode() + "'");
        }
        return template.getArea();
    }

    private LocalDate todayFor(UUID userId) {
        return dayService.today(dayService.zoneOf(identity.requireSettings(userId).getTimeZone()));
    }

    private static ApiException alreadyRunning() {
        return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, "You already have a Forge 66 program running.");
    }
}
