package com.dailyforge.points.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.domain.IdentityService;
import com.dailyforge.points.api.PointsDtos.LedgerEntryResponse;
import com.dailyforge.points.api.PointsDtos.SnapshotResponse;
import com.dailyforge.points.domain.PointsCategory;
import com.dailyforge.points.domain.PointsService;
import com.dailyforge.points.repo.PointsEntryRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader side of spec §7's points endpoints, scoped to the caller's own data
 * throughout (non-negotiable #8). There is no way to pass another user's id here — every
 * query starts from {@link CurrentUser#require()}, never from a path or query parameter.
 */
@RestController
@RequestMapping("/api/v1/points")
public class PointsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointsService points;
    private final PointsEntryRepository entries;
    private final IdentityService identity;
    private final DayService dayService;
    private final CurrentUser currentUser;

    public PointsController(
            PointsService points,
            PointsEntryRepository entries,
            IdentityService identity,
            DayService dayService,
            CurrentUser currentUser) {
        this.points = points;
        this.entries = entries;
        this.identity = identity;
        this.dayService = dayService;
        this.currentUser = currentUser;
    }

    @GetMapping("/snapshot")
    public SnapshotResponse snapshot() {
        UUID userId = currentUser.require();
        ZoneId zone = zoneOf(userId);
        return SnapshotResponse.of(points.snapshot(userId, zone));
    }

    @GetMapping("/ledger")
    public Page<LedgerEntryResponse> ledger(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) PointsCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = currentUser.require();
        PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        Page<com.dailyforge.points.domain.PointsEntry> found;
        if (from != null && to != null && category != null) {
            found =
                    entries.findAllByUserIdAndCategoryAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                            userId, category, from, to, pageable);
        } else if (from != null && to != null) {
            found = entries.findAllByUserIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(userId, from, to, pageable);
        } else if (category != null) {
            found = entries.findAllByUserIdAndCategoryOrderByOccurredOnDescCreatedAtDesc(userId, category, pageable);
        } else {
            found = entries.findAllByUserIdOrderByOccurredOnDescCreatedAtDesc(userId, pageable);
        }

        return found.map(LedgerEntryResponse::of);
    }

    /**
     * Rebuilds the caller's own score cache from the ledger (spec §5.1's "admin
     * recalculate endpoint"). There is no admin role yet, so this is scoped to the
     * caller's own account rather than left unimplemented — a user can always ask the
     * server to prove its cache agrees with their own ledger.
     */
    @PostMapping("/recalculate")
    public SnapshotResponse recalculate() {
        UUID userId = currentUser.require();
        points.recalculate(userId);
        return SnapshotResponse.of(points.snapshot(userId, zoneOf(userId)));
    }

    private ZoneId zoneOf(UUID userId) {
        return dayService.zoneOf(identity.requireSettings(userId).getTimeZone());
    }
}
