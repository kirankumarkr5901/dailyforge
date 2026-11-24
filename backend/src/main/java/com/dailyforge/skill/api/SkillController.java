package com.dailyforge.skill.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import com.dailyforge.skill.domain.SkillCatalog;
import com.dailyforge.skill.domain.SkillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Skill work: the three skills, their levels, and today's done marks. Nothing here is structural. */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skills;
    private final CurrentUser currentUser;

    public SkillController(SkillService skills, CurrentUser currentUser) {
        this.skills = skills;
        this.currentUser = currentUser;
    }

    public record LevelResponse(int number, String title, List<String> drills, String goal) {}

    public record SkillResponse(
            String code,
            String name,
            String when,
            String safety,
            int level,
            boolean doneOnDate,
            int daysDone,
            List<LevelResponse> levels) {

        static SkillResponse of(SkillService.SkillState s) {
            SkillCatalog.Skill skill = s.skill();
            List<LevelResponse> levels = java.util.stream.IntStream.range(0, skill.levels().size())
                    .mapToObj(i -> {
                        SkillCatalog.Level l = skill.levels().get(i);
                        return new LevelResponse(i + 1, l.title(), l.drills(), l.goal());
                    })
                    .toList();
            return new SkillResponse(
                    skill.code(), skill.name(), skill.when(), skill.safety(), s.level(), s.doneOnDate(), s.daysDone(), levels);
        }
    }

    public record BoardResponse(LocalDate date, List<SkillResponse> skills) {
        static BoardResponse of(SkillService.Board board) {
            return new BoardResponse(board.date(), board.skills().stream().map(SkillResponse::of).toList());
        }
    }

    public record WriteResponse(BoardResponse board, PointsEnvelope points) {
        static WriteResponse of(SkillService.Write write) {
            return new WriteResponse(BoardResponse.of(write.board()), PointsEnvelope.of(write.points()));
        }
    }

    public record LevelRequest(@NotNull Integer level) {}

    public record DoneRequest(@NotNull LocalDate date) {}

    /** {@code date} is optional: omitted means "today" in the user's own time zone. */
    @GetMapping
    public BoardResponse board(@RequestParam(required = false) LocalDate date) {
        UUID userId = currentUser.require();
        return BoardResponse.of(skills.board(userId, date != null ? date : skills.today(userId)));
    }

    @PutMapping("/{code}/level")
    public BoardResponse setLevel(@PathVariable String code, @Valid @RequestBody LevelRequest request) {
        return BoardResponse.of(skills.setLevel(currentUser.require(), code, request.level()));
    }

    @PostMapping("/{code}/done")
    public WriteResponse markDone(@PathVariable String code, @Valid @RequestBody DoneRequest request) {
        return WriteResponse.of(skills.markDone(currentUser.require(), code, request.date()));
    }

    @DeleteMapping("/{code}/done")
    public WriteResponse markNotDone(@PathVariable String code, @RequestParam LocalDate date) {
        return WriteResponse.of(skills.markNotDone(currentUser.require(), code, date));
    }
}
