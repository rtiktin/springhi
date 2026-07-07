package com.springhi.portfolio.service;

import com.springhi.portfolio.model.Asset;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.model.PortfolioSnapshot;
import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.repository.PortfolioSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PortfolioSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotService.class);

    private final AssetRepository assetRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;

    public PortfolioSnapshotService(AssetRepository assetRepository,
                                    PortfolioSnapshotRepository snapshotRepository,
                                    MarketDataService marketDataService,
                                    PortfolioService portfolioService,
                                    PortfolioRepository portfolioRepository) {
        this.assetRepository = assetRepository;
        this.snapshotRepository = snapshotRepository;
        this.marketDataService = marketDataService;
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
    }

    public void takeSnapshotsForAllUsers() {
        List<Long> portfolioIds = assetRepository.findDistinctPortfolioIds();
        if (portfolioIds.isEmpty()) {
            log.info("No portfolios with holdings to snapshot");
            return;
        }
        log.info("Taking portfolio snapshots for {} portfolio(s)", portfolioIds.size());
        for (Long portfolioId : portfolioIds) {
            try {
                takeSnapshotForPortfolio(portfolioId);
            } catch (Exception e) {
                log.error("Failed to take snapshot for portfolioId={}: {}", portfolioId, e.getMessage(), e);
            }
        }
    }

    public void takeSnapshotsIfNotTakenToday() {
        List<Long> portfolioIds = assetRepository.findDistinctPortfolioIds();
        if (portfolioIds.isEmpty()) return;
        LocalDate today = LocalDate.now();
        for (Long portfolioId : portfolioIds) {
            boolean alreadySnapshotted = snapshotRepository
                    .findFirstByPortfolioIdAndSnapshotDate(portfolioId, today)
                    .isPresent();
            if (!alreadySnapshotted) {
                try {
                    takeSnapshotForPortfolio(portfolioId);
                    log.info("Pre-refresh snapshot taken for portfolioId={}", portfolioId);
                } catch (Exception e) {
                    log.error("Failed pre-refresh snapshot for portfolioId={}: {}", portfolioId, e.getMessage(), e);
                }
            }
        }
    }

    public PortfolioSnapshot takeSnapshotForPortfolio(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        Long userId = portfolio != null ? portfolio.getUserId() : null;

        List<Asset> assets = assetRepository.findByPortfolioId(portfolioId);

        BigDecimal investedValue = assets.stream()
                .map(asset -> {
                    BigDecimal price = marketDataService.getLatestCachedQuote(asset.getSymbol())
                            .map(q -> q.getPrice())
                            .orElse(asset.getAveragePrice());
                    return asset.getQuantity().multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cashValue = portfolioService.getCashBalance(portfolioId);

        BigDecimal totalValue = investedValue.add(cashValue);
        LocalDate today = LocalDate.now();

        PortfolioSnapshot snapshot = snapshotRepository
                .findFirstByPortfolioIdAndSnapshotDate(portfolioId, today)
                .orElse(new PortfolioSnapshot());

        snapshot.setPortfolioId(portfolioId);
        snapshot.setUserId(userId != null ? userId : 0L);
        snapshot.setSnapshotDate(today);
        snapshot.setInvestedValue(investedValue);
        snapshot.setCashValue(cashValue);
        snapshot.setTotalValue(totalValue);
        snapshot.setSnapshotAt(LocalDateTime.now());

        PortfolioSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Snapshot saved for portfolioId={}: total={}", portfolioId, totalValue);
        return saved;
    }

    public List<PortfolioSnapshot> getSnapshotsForPortfolio(Long portfolioId) {
        return snapshotRepository.findByPortfolioIdOrderBySnapshotDateAsc(portfolioId);
    }
}
