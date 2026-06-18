package com.springhi.portfolio.controller;

import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.security.UserPrincipal;
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

    public PortfoliosController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
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
        return ResponseEntity.ok(portfolioService.createPortfolio(principal.getId(), name, description));
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
}
