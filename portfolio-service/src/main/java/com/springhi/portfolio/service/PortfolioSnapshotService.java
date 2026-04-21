package com.springhi.portfolio.service;

import com.springhi.portfolio.model.Asset;
import com.springhi.portfolio.model.PortfolioSnapshot;
import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.repository.PortfolioSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);

    private final AssetRepository assetRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;

    public PortfolioSnapshotService(AssetRepository assetRepository,
                                    PortfolioSnapshotRepository snapshotRepository,
                                    MarketDataService marketDataService,
                                    PortfolioService portfolioService) {
        this.assetRepository = assetRepository;
        this.snapshotRepository = snapshotRepository;
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
    }

    public void takeSnapshotsForAllUsers() {
        List<Long> userIds = assetRepository.findDistinctUserIds();
        if (userIds.isEmpty()) {
            log.info("No users with holdings to snapshot");
            return;
        }
        log.info("Taking portfolio snapshots for {} user(s)", userIds.size());
        for (Long userId : userIds) {
            try {
                takeSnapshotForUser(userId);
            } catch (Exception e) {
                log.error("Failed to take snapshot for userId={}: {}", userId, e.getMessage());
            }
        }
    }

    public PortfolioSnapshot takeSnapshotForUser(Long userId) {
        List<Asset> assets = assetRepository.findByUserId(userId);

        BigDecimal investedValue = assets.stream()
                .map(asset -> {
                    BigDecimal price = marketDataService.getLatestCachedQuote(asset.getSymbol())
                            .map(q -> q.getPrice())
                            .orElse(asset.getAveragePrice());
                    return asset.getQuantity().multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashValue = portfolioService.getCashBalance(userId);

        BigDecimal totalValue = investedValue.add(cashValue);
        LocalDate today = LocalDate.now();

        PortfolioSnapshot snapshot = snapshotRepository
                .findByUserIdAndSnapshotDate(userId, today)
                .orElse(new PortfolioSnapshot());

        snapshot.setUserId(userId);
        snapshot.setSnapshotDate(today);
        snapshot.setInvestedValue(investedValue);
        snapshot.setCashValue(cashValue);
        snapshot.setTotalValue(totalValue);

        PortfolioSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Snapshot saved for userId={}: total={}", userId, totalValue);
        return saved;
    }

    public List<PortfolioSnapshot> getSnapshotsForUser(Long userId) {
        return snapshotRepository.findByUserIdOrderBySnapshotDateAsc(userId);
    }
}
