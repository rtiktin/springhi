package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AiRunDetailsDto;
import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.LeaderboardEngagementDto;
import com.springhi.portfolio.dto.LeaderboardEntryDto;
import com.springhi.portfolio.dto.SubscribePromptConfigDto;
import com.springhi.portfolio.dto.SubscribePromptGateDto;
import com.springhi.portfolio.dto.PnlSummaryDto;
import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.dto.TransactionDto;
import com.springhi.portfolio.dto.TwrResponseDto;
import com.springhi.portfolio.model.PortfolioRecommendation;
import com.springhi.portfolio.repository.OptimizationScheduleRepository;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.LeaderboardService;
import com.springhi.portfolio.service.PortfolioService;
import com.springhi.portfolio.service.TwrService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PortfolioService portfolioService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final PortfolioProfileRepository profileRepository;
    private final OptimizationScheduleRepository scheduleRepository;
    private final TwrService twrService;

    public LeaderboardController(LeaderboardService leaderboardService,
                                 PortfolioService portfolioService,
                                 PortfolioRecommendationRepository recommendationRepository,
                                 PortfolioProfileRepository profileRepository,
                                 OptimizationScheduleRepository scheduleRepository,
                                 TwrService twrService) {
        this.leaderboardService = leaderboardService;
        this.portfolioService = portfolioService;
        this.recommendationRepository = recommendationRepository;
        this.profileRepository = profileRepository;
        this.scheduleRepository = scheduleRepository;
        this.twrService = twrService;
    }

    @GetMapping("/monthly")
    public ResponseEntity<List<LeaderboardEntryDto>> getMonthlyLeaderboard(
            @RequestParam String month,
            @RequestParam(required = false) String goal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getMonthlyLeaderboard(month, authHeader, goal));
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard(
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String goal,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getLeaderboard(range, scope, principal.getId(), authHeader, goal));
    }

    @PostMapping("/portfolio/{portfolioId}/click")
    public ResponseEntity<SubscribePromptGateDto> recordPortfolioClick(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(leaderboardService.recordClick(principal.getId(), portfolioId, authHeader, principal.isAdmin()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/subscribe-prompt/config")
    public ResponseEntity<SubscribePromptConfigDto> getSubscribePromptConfig(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getSubscribePromptConfig());
    }

    @PutMapping("/subscribe-prompt/config")
    public ResponseEntity<SubscribePromptConfigDto> updateSubscribePromptConfig(
            @RequestBody SubscribePromptConfigDto config,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(leaderboardService.updateSubscribePromptConfig(config));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/engagement")
    public ResponseEntity<List<LeaderboardEngagementDto>> getEngagement(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getEngagement(authHeader));
    }

    @GetMapping("/portfolio/{portfolioId}/twr")
    public ResponseEntity<TwrResponseDto> getPortfolioTwr(
            @PathVariable Long portfolioId,
            @RequestParam(required = false) String range,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || !principal.isAdmin()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(twrService.computeTwr(portfolioId, range));
    }

    private boolean subscriptionRequired(UserPrincipal principal, String authHeader) {
        return leaderboardService.shouldPrompt(principal.getId(), authHeader, principal.isAdmin());
    }

    @GetMapping("/portfolio/{portfolioId}/holdings")
    public ResponseEntity<List<AssetWithPrice>> getPortfolioHoldings(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getUserAssetsWithPrices(portfolioId));
    }

    @GetMapping("/portfolio/{portfolioId}/transactions")
    public ResponseEntity<List<TransactionDto>> getPortfolioTransactions(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getUserTransactions(portfolioId));
    }

    @GetMapping("/portfolio/{portfolioId}/cash")
    public ResponseEntity<Map<String, BigDecimal>> getPortfolioCash(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of("balance", portfolioService.getCashBalance(portfolioId)));
    }

    @GetMapping("/portfolio/{portfolioId}/pnl")
    public ResponseEntity<PnlSummaryDto> getPortfolioPnl(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getPnlSummary(portfolioId));
    }

    @GetMapping("/portfolio/{portfolioId}/recommendations/runs")
    public ResponseEntity<List<String>> getAiRunTimestamps(
            @PathVariable Long portfolioId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        List<String> timestamps = recommendationRepository
                .findDistinctGeneratedAtByPortfolioIdExcludingPendingOrderByDesc(portfolioId)
                .stream().map(LocalDateTime::toString).toList();
        return ResponseEntity.ok(timestamps);
    }

    @GetMapping("/portfolio/{portfolioId}/recommendations/run")
    public ResponseEntity<AiRunDetailsDto> getAiRunDetails(
            @PathVariable Long portfolioId,
            @RequestParam String generatedAt,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || subscriptionRequired(principal, authHeader)) return ResponseEntity.status(403).build();
        LocalDateTime ts = LocalDateTime.parse(generatedAt);
        List<PortfolioRecommendation> runRecs = recommendationRepository
                .findByPortfolioIdAndGeneratedAtOrderByActionDescIdAsc(portfolioId, ts);
        // Leaderboard viewers never see pending (not-yet-executed) recommendations; only show recs
        // the owner has acted on. Profile/schedule/confidence are still derived from the full run.
        List<RecommendationDto> recs = runRecs.stream()
                .filter(r -> !"PENDING".equalsIgnoreCase(r.getStatus()))
                .map(RecommendationDto::from).toList();
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
                            sectors,
                            false);
                })
                .orElseGet(() -> profileRepository.findByPortfolioId(portfolioId)
                        .map(PortfolioProfileDto::from).orElse(null));
        Long scheduleId = runRecs.stream()
                .map(PortfolioRecommendation::getScheduleId)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        String scheduleFrequency = scheduleId != null
                ? scheduleRepository.findById(scheduleId).map(s -> s.getFrequency()).orElse(null)
                : null;
        Integer confidenceScore = runRecs.stream()
                .map(PortfolioRecommendation::getConfidenceScore)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        return ResponseEntity.ok(new AiRunDetailsDto(recs, profileDto, scheduleId, scheduleFrequency, confidenceScore));
    }
}
