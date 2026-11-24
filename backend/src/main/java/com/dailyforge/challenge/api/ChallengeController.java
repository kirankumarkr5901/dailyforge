package com.dailyforge.challenge.api;

import com.dailyforge.challenge.domain.ChallengeService;
import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.security.StructuralChange;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Challenges. Starting and ending one is structural (it creates a habit), so both need
 * "Enable edit"; reading and claiming never do. The daily tick is the habit's own log.
 */
@RestController
@RequestMapping("/api/v1/challenges")
public class ChallengeController {

    private final ChallengeService challenges;
    private final CurrentUser currentUser;

    public ChallengeController(ChallengeService challenges, CurrentUser currentUser) {
        this.challenges = challenges;
        this.currentUser = currentUser;
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String title,
            String icon,
            Integer habitPoints,
            @NotNull Integer lengthDays,
            Integer bonusPoints) {}

    public record ClaimResponse(ChallengeService.View challenge, PointsEnvelope points) {}

    @GetMapping
    public List<ChallengeService.View> list() {
        return challenges.list(currentUser.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @StructuralChange
    public ChallengeService.View create(@Valid @RequestBody CreateRequest request) {
        return challenges.create(
                currentUser.require(),
                request.title().trim(),
                request.icon() == null || request.icon().isBlank() ? "trophy" : request.icon(),
                request.habitPoints() == null ? 10 : request.habitPoints(),
                request.lengthDays(),
                request.bonusPoints() == null ? 100 : request.bonusPoints());
    }

    @PostMapping("/{id}/claim")
    public ClaimResponse claim(@PathVariable UUID id) {
        var result = challenges.claim(currentUser.require(), id);
        return new ClaimResponse(result.challenge(), PointsEnvelope.of(result.points()));
    }

    @DeleteMapping("/{id}")
    @StructuralChange
    public ChallengeService.View abandon(@PathVariable UUID id) {
        return challenges.abandon(currentUser.require(), id);
    }
}
