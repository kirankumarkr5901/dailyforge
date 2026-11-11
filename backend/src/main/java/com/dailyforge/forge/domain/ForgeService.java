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

    public ForgeService(
            ForgeProgramRepository programs,
            ForgeQuestRepository quests,
            ForgeQuestTemplateRepository templates,
            ForgeSettingsProvider settingsProvider,
            PointsService points,
            DayService dayService,
            IdentityService identity,
            ObjectMapper objectMapper) {
        this.programs = programs;
        this.quests = quests;
        this.templates = templates;
        this.settingsProvider = settingsProvider;
        this.points = points;
        this.dayService = dayService;
        this.identity = identity;
        this.objectMapper = objectMapper;
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
        closeIfFinished(userId, today);
        return buildOverview(userId, today);
    }

    @Transactional
    public Overview start(UUID userId, Map<String, List<String>> answers, ForgeMode mode) {
        if (mode == null) {
            throw ApiException.outOfRange("mode", "Choose Normal or Hard mode.");
        }
        LocalDate today = todayFor(userId);
        closeIfFinished(userId, today);
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

        long seed = program.getId().getMostSignificantBits() ^ program.getId().getLeastSignificantBits();
        List<ForgeQuest> planned =
                PlanGenerator.generate(seed, today, settings, mode, focus, scores, templates.findAll()).stream()
                        .map(p -> ForgeQuest.planned(program.getId(), userId, p.date(), p.dayNumber(), p.templateCode(), p.xp()))
                        .toList();
        quests.saveAll(planned);

        return buildOverview(userId, today);
    }

    /** Ends the running program from today. Closed days keep their history and XP. */
    @Transactional
    public Overview abandon(UUID userId) {
        LocalDate today = todayFor(userId);
        closeIfFinished(userId, today);
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
        closeIfFinished(userId, today);

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
