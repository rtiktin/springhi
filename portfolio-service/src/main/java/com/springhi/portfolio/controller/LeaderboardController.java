package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AiRunDetailsDto;
import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.LeaderboardEntryDto;
import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.dto.TransactionDto;
import com.springhi.portfolio.model.PortfolioRecommendation;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.LeaderboardService;
import com.springhi.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PortfolioService portfolioService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final PortfolioProfileRepository profileRepository;

    public LeaderboardController(LeaderboardService leaderboardService,
                                 PortfolioService portfolioService,
                                 PortfolioRecommendationRepository recommendationRepository,
                                 PortfolioProfileRepository profileRepository) {
        this.leaderboardService = leaderboardService;
        this.portfolioService = portfolioService;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard(
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "all") String scope,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getLeaderboard(range, scope, principal.getId(), authHeader));
    }

    @GetMapping("/portfolio/{portfolioId}/holdings")
    public ResponseEntity<List<AssetWithPrice>> getPortfolioHoldings(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getUserAssetsWithPrices(portfolioId));
    }

    @GetMapping("/portfolio/{portfolioId}/transactions")
    public ResponseEntity<List<TransactionDto>> getPortfolioTransactions(
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getUserTransactions(portfolioId));
    }

    @GetMapping("/portfolio/{portfolioId}/recommendations/run")
    public ResponseEntity<AiRunDetailsDto> getAiRunDetails(
            @PathVariable Long portfolioId,
            @RequestParam String generatedAt,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        LocalDateTime ts = LocalDateTime.parse(generatedAt);
        List<PortfolioRecommendation> runRecs = recommendationRepository
                .findByPortfolioIdAndGeneratedAtOrderByActionDescIdAsc(portfolioId, ts);
        List<RecommendationDto> recs = runRecs.stream().map(RecommendationDto::from).toList();
        PortfolioProfileDto profileDto = runRecs.stream()
                .filter(r -> r.getSnapshotRiskLevel() != null || r.getSnapshotGoal() != null
                        || r.getSnapshotHorizonYears() != null || r.getSnapshotAdditionalComments() != null)
                .findFirst()
                .map(r -> {
                    List<String> sectors = (r.getSnapshotSectorConstraints() != null
                            && !r.getSnapshotSectorConstraints().isBlank())
                            ? Arrays.stream(r.getSnapshotSectorConstraints().split(","))
                                    .map(String::trim).filter(s -> !s.isBlank()).toList()
                            : List.of();
                    return new PortfolioProfileDto(
                            portfolioId,
                            r.getSnapshotRiskLevel(),
                            r.getSnapshotGoal(),
                            r.getSnapshotHorizonYears(),
                            r.getSnapshotLiquidityNeeds(),
                            r.getSnapshotAdditionalComments(),
                            r.getSnapshotCurrency() != null ? r.getSnapshotCurrency() : "USD",
                            sectors);
                })
                .orElseGet(() -> profileRepository.findByPortfolioId(portfolioId)
                        .map(PortfolioProfileDto::from).orElse(null));
        return ResponseEntity.ok(new AiRunDetailsDto(recs, profileDto));
    }
}
