package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.model.PortfolioSnapshot;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioService;
import com.springhi.portfolio.service.PortfolioSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);
    private final PortfolioService portfolioService;
    private final PortfolioSnapshotService snapshotService;

    public PortfolioController(PortfolioService portfolioService, PortfolioSnapshotService snapshotService) {
        this.portfolioService = portfolioService;
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public ResponseEntity<List<AssetWithPrice>> getHoldings(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        log.info("getHoldings for portfolioId={}, userId={}", portfolioId, principal.getId());
        List<AssetWithPrice> result = portfolioService.getUserAssetsWithPrices(portfolioId);
        log.info("Returning {} holdings for portfolioId={}", result.size(), portfolioId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        log.info("getTransactions for portfolioId={}", portfolioId);
        return ResponseEntity.ok(portfolioService.getUserTransactions(portfolioId));
    }

    @PostMapping("/transactions")
    public ResponseEntity<Transaction> addTransaction(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Transaction transaction) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        log.info("addTransaction for portfolioId={}: {} {} @ {}",
                portfolioId, transaction.getType(), transaction.getSymbol(), transaction.getPrice());
        transaction.setUserId(principal.getId());
        transaction.setPortfolioId(portfolioId);
        return ResponseEntity.ok(portfolioService.processTransaction(transaction));
    }

    @GetMapping("/cash")
    public ResponseEntity<Map<String, java.math.BigDecimal>> getCashBalance(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        java.math.BigDecimal balance = portfolioService.getCashBalance(portfolioId);
        return ResponseEntity.ok(Map.of("balance", balance));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<PortfolioSnapshot>> getSnapshots(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return ResponseEntity.ok(snapshotService.getSnapshotsForPortfolio(portfolioId));
    }

    @PostMapping("/snapshots")
    public ResponseEntity<PortfolioSnapshot> takeSnapshot(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return ResponseEntity.ok(snapshotService.takeSnapshotForPortfolio(portfolioId));
    }

    @GetMapping("/company-name/{symbol}")
    public ResponseEntity<Map<String, String>> getCompanyName(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String symbol) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return portfolioService.getOrFetchCompanyName(portfolioId, symbol.toUpperCase())
                .map(name -> ResponseEntity.ok(Map.of("symbol", symbol.toUpperCase(), "companyName", name)))
                .orElse(ResponseEntity.ok(Map.of("symbol", symbol.toUpperCase(), "companyName", "")));
    }

    @GetMapping("/default-portfolio")
    public ResponseEntity<Portfolio> getOrCreateDefaultPortfolio(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(portfolioService.getOrCreateDefaultPortfolio(principal.getId()));
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Portfolio service is reachable and secured");
    }
}
