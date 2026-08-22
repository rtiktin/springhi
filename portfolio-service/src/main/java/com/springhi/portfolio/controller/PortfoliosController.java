package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioProfileService;
import com.springhi.portfolio.service.PortfolioService;
import com.springhi.portfolio.service.UserServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfoliosController {

    private final PortfolioService portfolioService;
    private final PortfolioProfileService portfolioProfileService;
    private final UserServiceClient userServiceClient;
    private final PortfolioRecommendationRepository recommendationRepository;

    public PortfoliosController(PortfolioService portfolioService,
                                PortfolioProfileService portfolioProfileService,
                                UserServiceClient userServiceClient,
                                PortfolioRecommendationRepository recommendationRepository) {
        this.portfolioService = portfolioService;
        this.portfolioProfileService = portfolioProfileService;
        this.userServiceClient = userServiceClient;
        this.recommendationRepository = recommendationRepository;
    }

    @GetMapping("/usage-stats")
    public ResponseEntity<Map<String, Object>> getUsageStats(@AuthenticationPrincipal UserPrincipal principal,
                                                              HttpServletRequest request) {
        if (principal == null) return ResponseEntity.status(403).build();

        Map<String, Object> limits = userServiceClient.getSubscriptionLimits(
                principal.getId(), request.getHeader("Authorization")).orElse(null);
        
        if (limits != null) {
            int maxPortfolios = limits.get("maxPortfolios") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
            portfolioService.enforceLimits(principal.getId(), maxPortfolios);
        }

        int portfolioCount = (int) portfolioService.listPortfolios(principal.getId()).stream()
                .filter(Portfolio::isEnabled)
                .count();

        String planName = limits != null ? (String) limits.getOrDefault("planName", "FREE") : "FREE";
        boolean isFree = "FREE".equalsIgnoreCase(planName);
        LocalDateTime countSince = isFree
                ? LocalDateTime.of(2000, 1, 1, 0, 0)
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long optimizationCount = recommendationRepository.countOptimizationRunsSince(principal.getId(), countSince);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("portfolioCount", portfolioCount);
        stats.put("optimizationsThisMonth", optimizationCount);
        stats.put("isFreeLimit", isFree);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/creation-count")
    public ResponseEntity<Map<String, Integer>> getCreationCount(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        int count = portfolioService.getTotalPortfoliosCreated(principal.getId());
        return ResponseEntity.ok(Map.of("totalCreated", count));
    }

    @GetMapping
    public ResponseEntity<List<Portfolio>> list(@AuthenticationPrincipal UserPrincipal principal,
                                               HttpServletRequest request) {
        if (principal == null) return ResponseEntity.status(403).build();
        
        // Proactively enforce limits on list
        userServiceClient.getSubscriptionLimits(principal.getId(), request.getHeader("Authorization"))
                .ifPresent(limits -> {
                    int maxPortfolios = limits.get("maxPortfolios") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
                    portfolioService.enforceLimits(principal.getId(), maxPortfolios);
                });

        return ResponseEntity.ok(portfolioService.listPortfolios(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        if (principal == null) return ResponseEntity.status(403).build();

        String authHeader = request.getHeader("Authorization");
        Map<String, Object> limits = userServiceClient.getSubscriptionLimits(principal.getId(), authHeader).orElse(null);
        if (limits != null) {
            int maxPortfolios = limits.get("maxPortfolios") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
            portfolioService.enforceLimits(principal.getId(), maxPortfolios);
            int currentCount = (int) portfolioService.listPortfolios(principal.getId()).stream()
                    .filter(Portfolio::isEnabled)
                    .count();
            if (currentCount >= maxPortfolios) {
                String planName = (String) limits.getOrDefault("planName", "FREE");
                return ResponseEntity.status(429).body(Map.of(
                        "error", "PORTFOLIO_LIMIT_REACHED",
                        "message", "You have reached the maximum number of portfolios (" + maxPortfolios + ") for your " + planName + " plan. Please upgrade to create more.",
                        "maxPortfolios", maxPortfolios,
                        "planName", planName
                ));
            }
        }

        String name = body.getOrDefault("name", "New Portfolio").trim();
        if (name.isBlank()) name = "New Portfolio";
        String description = body.get("description");
        String competitionMonthStr = body.get("competitionMonth");
        LocalDate competitionMonth = null;
        if (competitionMonthStr != null && !competitionMonthStr.isBlank()) {
            competitionMonth = LocalDate.parse(competitionMonthStr + "-01");
        }
        Portfolio created = portfolioService.createPortfolio(principal.getId(), name, description, competitionMonth);
        portfolioProfileService.initFromInvestorProfile(created.getId(), principal.getId());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Portfolio> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        if (principal == null) return ResponseEntity.status(403).build();
        String name = body.getOrDefault("name", "").trim();
        String description = body.get("description");
        return ResponseEntity.ok(portfolioService.updatePortfolio(principal.getId(), id, name, description));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.deletePortfolio(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<PortfolioProfileDto> getProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), id);
        return portfolioProfileService.getProfile(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(
                        portfolioProfileService.initFromInvestorProfile(id, principal.getId())
                ));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<PortfolioProfileDto> saveProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody PortfolioProfileDto dto) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), id);
        return ResponseEntity.ok(portfolioProfileService.saveProfile(id, dto));
    }
}
