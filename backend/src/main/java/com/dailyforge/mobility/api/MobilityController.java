package com.dailyforge.mobility.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.mobility.domain.MobilityCatalog;
import com.dailyforge.mobility.domain.MobilityService;
import com.dailyforge.points.api.PointsDtos.PointsEnvelope;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The guided mobility flows, and recording one as finished today. */
@RestController
@RequestMapping("/api/v1/mobility")
public class MobilityController {

    private final MobilityService mobility;
    private final CurrentUser currentUser;

    public MobilityController(MobilityService mobility, CurrentUser currentUser) {
        this.mobility = mobility;
        this.currentUser = currentUser;
    }

    public record MoveResponse(String name, int seconds, String cue) {}

    public record FlowResponse(
            String code, String name, String when, int totalSeconds, boolean doneToday, int timesDone, List<MoveResponse> moves) {
        static FlowResponse of(MobilityService.FlowState s) {
            MobilityCatalog.Flow f = s.flow();
            return new FlowResponse(
                    f.code(),
                    f.name(),
                    f.when(),
                    f.totalSeconds(),
                    s.doneToday(),
                    s.timesDone(),
                    f.moves().stream().map(m -> new MoveResponse(m.name(), m.seconds(), m.cue())).toList());
        }
    }

    public record BoardResponse(LocalDate date, List<FlowResponse> flows) {
        static BoardResponse of(MobilityService.Board board) {
            return new BoardResponse(board.date(), board.flows().stream().map(FlowResponse::of).toList());
        }
    }

    public record CompletionResponse(BoardResponse board, PointsEnvelope points) {}

    @GetMapping
    public BoardResponse board() {
        return BoardResponse.of(mobility.board(currentUser.require()));
    }

    /** Called by the player when the last move's countdown runs out. */
    @PostMapping("/{code}/complete")
    public CompletionResponse complete(@PathVariable String code) {
        var result = mobility.complete(currentUser.require(), code);
        return new CompletionResponse(BoardResponse.of(result.board()), PointsEnvelope.of(result.points()));
    }
}
