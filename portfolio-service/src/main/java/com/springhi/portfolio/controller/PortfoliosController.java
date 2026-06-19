package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioProfileService;
import com.springhi.portfolio.service.PortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfoliosController {

    private final PortfolioService portfolioService;
    private final PortfolioProfileService portfolioProfileService;

    public PortfoliosController(PortfolioService portfolioService,
                                PortfolioProfileService portfolioProfileService) {
        this.portfolioService = portfolioService;
        this.portfolioProfileService = portfolioProfileService;
    }

    @GetMapping
    public ResponseEntity<List<Portfolio>> list(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.listPortfolios(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<Portfolio> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        if (principal == null) return ResponseEntity.status(403).build();
        String name = body.getOrDefault("name", "New Portfolio").trim();
        if (name.isBlank()) name = "New Portfolio";
        String description = body.get("description");
        Portfolio created = portfolioService.createPortfolio(principal.getId(), name, description);
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
