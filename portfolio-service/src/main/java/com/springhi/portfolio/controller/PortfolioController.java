package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AssetWithPrice;
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
    public ResponseEntity<List<AssetWithPrice>> getHoldings(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            log.error("getHoldings called with null principal — request was not authenticated");
            return ResponseEntity.status(403).build();
        }
        log.info("getHoldings for userId={}", principal.getId());
        try {
            List<AssetWithPrice> result = portfolioService.getUserAssetsWithPrices(principal.getId());
            log.info("Returning {} holdings for userId={}", result.size(), principal.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching holdings for userId={}: {}", principal.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            log.error("getTransactions called with null principal — request was not authenticated");
            return ResponseEntity.status(403).build();
        }
        log.info("getTransactions for userId={}", principal.getId());
        try {
            List<Transaction> result = portfolioService.getUserTransactions(principal.getId());
            log.info("Returning {} transactions for userId={}", result.size(), principal.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching transactions for userId={}: {}", principal.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/transactions")
    public ResponseEntity<Transaction> addTransaction(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Transaction transaction
    ) {
        log.info("addTransaction for userId={}: {} {} @ {}", principal.getId(), transaction.getType(), transaction.getSymbol(), transaction.getPrice());
        transaction.setUserId(principal.getId());
        return ResponseEntity.ok(portfolioService.processTransaction(transaction));
    }

    @GetMapping("/cash")
    public ResponseEntity<java.util.Map<String, java.math.BigDecimal>> getCashBalance(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        java.math.BigDecimal balance = portfolioService.getCashBalance(principal.getId());
        return ResponseEntity.ok(java.util.Map.of("balance", balance));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<PortfolioSnapshot>> getSnapshots(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(snapshotService.getSnapshotsForUser(principal.getId()));
    }

    @PostMapping("/snapshots")
    public ResponseEntity<PortfolioSnapshot> takeSnapshot(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(snapshotService.takeSnapshotForUser(principal.getId()));
    }

    @GetMapping("/company-name/{symbol}")
    public ResponseEntity<java.util.Map<String, String>> getCompanyName(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String symbol) {
        if (principal == null) return ResponseEntity.status(403).build();
        return portfolioService.getOrFetchCompanyName(principal.getId(), symbol.toUpperCase())
                .map(name -> ResponseEntity.ok(java.util.Map.of("symbol", symbol.toUpperCase(), "companyName", name)))
                .orElse(ResponseEntity.ok(java.util.Map.of("symbol", symbol.toUpperCase(), "companyName", "")));
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Portfolio service is reachable and secured");
    }
}
