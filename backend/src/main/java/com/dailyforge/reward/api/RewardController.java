package com.dailyforge.reward.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.reward.api.RewardDtos.CreateRewardRequest;
import com.dailyforge.reward.api.RewardDtos.RedeemResponse;
import com.dailyforge.reward.api.RewardDtos.RedemptionResponse;
import com.dailyforge.reward.api.RewardDtos.RefundResponse;
import com.dailyforge.reward.api.RewardDtos.RewardResponse;
import com.dailyforge.reward.domain.RewardService;
import jakarta.validation.Valid;
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

/** Rewards and redemption (spec §8.9) — the sink the points economy needs to be a real loop. */
@RestController
@RequestMapping("/api/v1")
public class RewardController {

    private final RewardService rewardService;
    private final CurrentUser currentUser;

    public RewardController(RewardService rewardService, CurrentUser currentUser) {
        this.rewardService = rewardService;
        this.currentUser = currentUser;
    }

    @GetMapping("/rewards")
    public List<RewardResponse> list() {
        return rewardService.list(currentUser.require()).stream().map(RewardResponse::of).toList();
    }

    @PostMapping("/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    public RewardResponse create(@Valid @RequestBody CreateRewardRequest request) {
        var reward =
                rewardService.create(currentUser.require(), request.name().trim(), request.cost(), request.icon(), request.isRepeatable(), request.stock());
        return RewardResponse.of(reward);
    }

    @DeleteMapping("/rewards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        rewardService.archive(id, currentUser.require());
    }

    @PostMapping("/rewards/{id}/redeem")
    public RedeemResponse redeem(@PathVariable UUID id) {
        var result = rewardService.redeem(id, currentUser.require());
        return RedeemResponse.of(result.redemption(), result.points());
    }

    @GetMapping("/reward-redemptions")
    public List<RedemptionResponse> redemptions() {
        return rewardService.listRedemptions(currentUser.require()).stream().map(RedemptionResponse::of).toList();
    }

    @DeleteMapping("/reward-redemptions/{id}")
    public RefundResponse refund(@PathVariable UUID id) {
        return RefundResponse.of(rewardService.refund(id, currentUser.require()));
    }
}
