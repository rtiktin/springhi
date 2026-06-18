package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.RecommendationDto;
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

    public PortfolioOptimizationController(PortfolioOptimizationService optimizationService,
                                           PortfolioRecommendationRepository recommendationRepository,
                                           PortfolioService portfolioService) {
        this.optimizationService = optimizationService;
        this.recommendationRepository = recommendationRepository;
        this.portfolioService = portfolioService;
    }

    @PostMapping("/optimize")
    public ResponseEntity<OptimizationResponse> optimize(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        log.info("Portfolio optimization requested for portfolioId={}, userId={}", portfolioId, principal.getId());
        OptimizationResponse result = optimizationService.optimize(principal.getId(), portfolioId);
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
}
