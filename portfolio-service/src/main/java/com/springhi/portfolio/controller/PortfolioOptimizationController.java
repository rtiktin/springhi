package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AiRunDetailsDto;
import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.model.PortfolioRecommendation;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioOptimizationService;
import com.springhi.portfolio.service.PortfolioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioOptimizationController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizationController.class);

    private final PortfolioOptimizationService optimizationService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final PortfolioService portfolioService;
    private final PortfolioProfileRepository profileRepository;

    public PortfolioOptimizationController(PortfolioOptimizationService optimizationService,
                                           PortfolioRecommendationRepository recommendationRepository,
                                           PortfolioService portfolioService,
                                           PortfolioProfileRepository profileRepository) {
        this.optimizationService = optimizationService;
        this.recommendationRepository = recommendationRepository;
        this.portfolioService = portfolioService;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/optimize")
    public ResponseEntity<?> optimize(
            @RequestParam Long portfolioId,
            @RequestParam(defaultValue = "gemini") String provider,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        if (!principal.isEmailVerified()) {
            return ResponseEntity.status(403).body(Map.of("error", "EMAIL_NOT_VERIFIED", "message", "Please verify your email address before running optimization."));
        }
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        log.info("Portfolio optimization requested for portfolioId={}, userId={}, provider={}", portfolioId, principal.getId(), provider);
        OptimizationResponse result = optimizationService.optimize(principal.getId(), portfolioId, provider);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationDto>> getTodayRecommendations(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return ResponseEntity.ok(optimizationService.getTodayRecommendations(portfolioId));
    }

    @PostMapping("/recommendations/{id}/execute")
    public ResponseEntity<RecommendationDto> markExecuted(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return recommendationRepository.findById(id)
                .filter(r -> portfolioId.equals(r.getPortfolioId()))
                .map(r -> {
                    r.setStatus("EXECUTED");
                    if (body.get("transactionId") instanceof Number n)
                        r.setTransactionId(n.longValue());
                    if (body.get("actualAmount") instanceof Number n)
                        r.setEstimatedValue(new java.math.BigDecimal(n.toString()));
                    r.setExecutedAt(LocalDateTime.now());
                    return ResponseEntity.ok(RecommendationDto.from(recommendationRepository.save(r)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/recommendations/{id}/skip")
    public ResponseEntity<RecommendationDto> markSkipped(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return recommendationRepository.findById(id)
                .filter(r -> portfolioId.equals(r.getPortfolioId()))
                .map(r -> {
                    r.setStatus("SKIPPED");
                    return ResponseEntity.ok(RecommendationDto.from(recommendationRepository.save(r)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recommendations/run")
    public ResponseEntity<AiRunDetailsDto> getAiRunDetails(
            @RequestParam Long portfolioId,
            @RequestParam String generatedAt,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        LocalDateTime ts = LocalDateTime.parse(generatedAt);
        List<PortfolioRecommendation> runRecs = recommendationRepository
                .findByPortfolioIdAndGeneratedAtOrderByActionDescIdAsc(portfolioId, ts);
        List<RecommendationDto> recs = runRecs.stream().map(RecommendationDto::from).toList();
        PortfolioProfileDto currentProfile = profileRepository.findByPortfolioId(portfolioId)
                .map(PortfolioProfileDto::from).orElse(null);
        PortfolioProfileDto profileDto = runRecs.stream()
                .filter(r -> r.getSnapshotRiskLevel() != null || r.getSnapshotGoal() != null
                        || r.getSnapshotHorizonYears() != null || r.getSnapshotAdditionalComments() != null
                        || r.getSnapshotLiquidityNeeds() != null || r.getSnapshotSectorConstraints() != null)
                .findFirst()
                .map(r -> {
                    java.util.List<String> sectors = (r.getSnapshotSectorConstraints() != null
                            && !r.getSnapshotSectorConstraints().isBlank())
                            ? java.util.Arrays.stream(r.getSnapshotSectorConstraints().split(","))
                                    .map(String::trim).filter(s -> !s.isBlank()).toList()
                            : (currentProfile != null ? currentProfile.sectorConstraints() : java.util.List.of());
                    return new PortfolioProfileDto(
                            portfolioId,
                            r.getSnapshotRiskLevel() != null ? r.getSnapshotRiskLevel() : (currentProfile != null ? currentProfile.riskLevel() : null),
                            r.getSnapshotGoal() != null ? r.getSnapshotGoal() : (currentProfile != null ? currentProfile.goal() : null),
                            r.getSnapshotHorizonYears() != null ? r.getSnapshotHorizonYears() : (currentProfile != null ? currentProfile.horizonYears() : null),
                            r.getSnapshotLiquidityNeeds() != null ? r.getSnapshotLiquidityNeeds() : (currentProfile != null ? currentProfile.liquidityNeeds() : null),
                            r.getSnapshotAdditionalComments() != null ? r.getSnapshotAdditionalComments() : (currentProfile != null ? currentProfile.additionalComments() : null),
                            r.getSnapshotCurrency() != null ? r.getSnapshotCurrency() : "USD",
                            sectors);
                })
                .orElse(currentProfile);
        return ResponseEntity.ok(new AiRunDetailsDto(recs, profileDto));
    }
}
