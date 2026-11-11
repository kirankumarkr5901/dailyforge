package com.dailyforge.forge.api;

import com.dailyforge.forge.domain.ForgeArea;
import com.dailyforge.forge.domain.ForgeMode;
import com.dailyforge.forge.domain.ForgeProgram;
import com.dailyforge.forge.domain.ForgeProgramStatus;
import com.dailyforge.forge.domain.ForgeProgressCalculator.DayResult;
import com.dailyforge.forge.domain.ForgeProgressCalculator.DayStatus;
import com.dailyforge.forge.domain.ForgeProgressCalculator.Progress;
import com.dailyforge.forge.domain.ForgeQuest;
import com.dailyforge.forge.domain.ForgeQuestTemplate;
import com.dailyforge.forge.domain.ForgeQuestionnaire;
import com.dailyforge.forge.domain.ForgeService.Overview;
import com.dailyforge.forge.domain.ForgeService.QuestChange;
import com.dailyforge.forge.domain.ForgeSettings;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ForgeDtos {

    private ForgeDtos() {}

    // ---- Questionnaire ------------------------------------------------------

    /** Weights stay on the server: the client shows questions, the server scores them. */
    public record OptionResponse(String code, String label) {}

    public record QuestionResponse(String code, String prompt, String hint, int maxPicks, List<OptionResponse> options) {}

    public record QuestionnaireResponse(int lengthDays, List<QuestionResponse> questions) {
        public static QuestionnaireResponse of(int lengthDays) {
            return new QuestionnaireResponse(
                    lengthDays,
                    ForgeQuestionnaire.QUESTIONS.stream()
                            .map(
                                    q ->
                                            new QuestionResponse(
                                                    q.code(),
                                                    q.prompt(),
                                                    q.hint(),
                                                    q.maxPicks(),
                                                    q.options().stream()
                                                            .map(o -> new OptionResponse(o.code(), o.label()))
                                                            .toList()))
                            .toList());
        }
    }

    /** {@code answers}: question code → the option code(s) picked. */
    public record StartRequest(@NotNull Map<String, List<String>> answers, @NotNull ForgeMode mode) {}

    // ---- Overview -----------------------------------------------------------

    public record StatResponse(ForgeArea area, int xp) {}

    public record ProfileResponse(
            int level,
            int totalXp,
            int xpIntoLevel,
            int xpForNextLevel,
            int currentStreak,
            int bestStreak,
            int daysCompleted,
            List<StatResponse> stats) {

        static ProfileResponse of(Progress progress) {
            return new ProfileResponse(
                    progress.level().level(),
                    progress.totalXp(),
                    progress.level().xpIntoLevel(),
                    progress.level().xpForNextLevel(),
                    progress.currentStreak(),
                    progress.bestStreak(),
                    progress.daysCompleted(),
                    Arrays.stream(ForgeArea.values())
                            .map(a -> new StatResponse(a, progress.stats().getOrDefault(a, 0)))
                            .toList());
        }
    }

    /** {@code points} is what completing it pays into the main score — server-computed. */
    public record QuestResponse(
            UUID id,
            String code,
            String title,
            String description,
            ForgeArea area,
            int tier,
            int xp,
            int points,
            boolean done) {}

    public record CalendarDayResponse(LocalDate date, int dayNumber, int done, int total, DayStatus status) {
        static CalendarDayResponse of(DayResult day) {
            return new CalendarDayResponse(day.date(), day.dayNumber(), day.done(), day.total(), day.status());
        }
    }

    public record ProgramResponse(
            UUID id,
            ForgeMode mode,
            LocalDate startDate,
            LocalDate endDate,
            int lengthDays,
            int dayNumber,
            int phase,
            List<ForgeArea> focusAreas,
            List<QuestResponse> quests,
            boolean dayComplete,
            List<CalendarDayResponse> calendar) {}

    /** A program that has ended, for the "you finished" or "start again" screen. */
    public record FinishedProgramResponse(
            UUID id,
            ForgeProgramStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int lengthDays,
            int daysCompleted) {}

    public record OverviewResponse(
            LocalDate today,
            ProfileResponse profile,
            ProgramResponse program,
            FinishedProgramResponse lastProgram) {

        public static OverviewResponse of(Overview overview) {
            Progress progress = overview.progress();
            ForgeSettings settings = overview.settings();

            ProgramResponse program =
                    overview.activeProgram()
                            .map(p -> programOf(p, overview, progress, settings))
                            .orElse(null);

            FinishedProgramResponse last =
                    overview.lastFinishedProgram()
                            .map(
                                    p -> {
                                        List<DayResult> days = progress.days().getOrDefault(p.getId(), List.of());
                                        int completed =
                                                (int) days.stream().filter(d -> d.status() == DayStatus.DONE).count();
                                        return new FinishedProgramResponse(
                                                p.getId(), p.getStatus(), p.getStartDate(), p.getEndDate(), days.size(), completed);
                                    })
                            .orElse(null);

            return new OverviewResponse(overview.today(), ProfileResponse.of(progress), program, last);
        }

        private static ProgramResponse programOf(
                ForgeProgram p, Overview overview, Progress progress, ForgeSettings settings) {
            int dayNumber = (int) (overview.today().toEpochDay() - p.getStartDate().toEpochDay()) + 1;
            List<DayResult> days = progress.days().getOrDefault(p.getId(), List.of());

            List<QuestResponse> quests =
                    overview.todaysQuests().stream()
                            .map(q -> questOf(q, overview.templatesByCode().get(q.getTemplateCode()), settings))
                            .toList();
            boolean dayComplete = !quests.isEmpty() && quests.stream().allMatch(QuestResponse::done);

            return new ProgramResponse(
                    p.getId(),
                    p.getMode(),
                    p.getStartDate(),
                    p.getEndDate(),
                    days.size(),
                    dayNumber,
                    settings.phaseOf(dayNumber),
                    p.getFocusAreas(),
                    quests,
                    dayComplete,
                    days.stream().map(CalendarDayResponse::of).toList());
        }

        private static QuestResponse questOf(ForgeQuest q, ForgeQuestTemplate t, ForgeSettings settings) {
            return new QuestResponse(
                    q.getId(),
                    t.getCode(),
                    t.getTitle(),
                    t.getDescription(),
                    t.getArea(),
                    t.getTier(),
                    q.getXp(),
                    settings.pointsFor(q.getXp()),
                    q.isDone());
        }
    }

    /**
     * A quest marked or unmarked. {@code points} is the main score's envelope, applied
     * to the header pill as-is; {@code leveledUp} and {@code dayCompleted} drive the
     * Forge screen's own feedback.
     */
    public record QuestChangeResponse(
            OverviewResponse forge, PointsEnvelope points, boolean leveledUp, boolean dayCompleted) {

        public static QuestChangeResponse of(QuestChange change) {
            OverviewResponse overview = OverviewResponse.of(change.overview());
            return new QuestChangeResponse(
                    overview,
                    PointsEnvelope.of(change.points()),
                    overview.profile().level() > change.levelBefore(),
                    change.dayCompleted());
        }
    }
}
