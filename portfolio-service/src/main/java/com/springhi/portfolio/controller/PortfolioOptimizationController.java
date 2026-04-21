package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioOptimizationService;
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

    public PortfolioOptimizationController(PortfolioOptimizationService optimizationService,
                                           PortfolioRecommendationRepository recommendationRepository) {
        this.optimizationService = optimizationService;
        this.recommendationRepository = recommendationRepository;
    }

    @PostMapping("/optimize")
    public ResponseEntity<OptimizationResponse> optimize(@AuthenticationPrincipal UserPrincipal principal) {
        log.info("Portfolio optimization requested for userId={}", principal.getId());
        OptimizationResponse result = optimizationService.optimize(principal.getId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationDto>> getTodayRecommendations(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(optimizationService.getTodayRecommendations(principal.getId()));
    }

    @PostMapping("/recommendations/{id}/execute")
    public ResponseEntity<RecommendationDto> markExecuted(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return recommendationRepository.findById(id)
                .filter(r -> r.getUserId().equals(principal.getId()))
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
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return recommendationRepository.findById(id)
                .filter(r -> r.getUserId().equals(principal.getId()))
                .map(r -> {
                    r.setStatus("SKIPPED");
                    return ResponseEntity.ok(RecommendationDto.from(recommendationRepository.save(r)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
