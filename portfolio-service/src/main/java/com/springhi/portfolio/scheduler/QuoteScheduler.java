package com.springhi.portfolio.scheduler;

import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.service.MarketDataService;
import com.springhi.portfolio.service.PortfolioSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class QuoteScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteScheduler.class);

    private final AssetRepository assetRepository;
    private final MarketDataService marketDataService;
    private final PortfolioSnapshotService snapshotService;

    public QuoteScheduler(AssetRepository assetRepository,
                          MarketDataService marketDataService,
                          PortfolioSnapshotService snapshotService) {
        this.assetRepository = assetRepository;
        this.marketDataService = marketDataService;
        this.snapshotService = snapshotService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("Startup quote refresh triggered");
        refreshAllHeldSymbols();
    }

    @Scheduled(cron = "0 0 15 * * MON-FRI", zone = "America/New_York")
    public void refreshAllHeldSymbols() {
        List<String> symbols = assetRepository.findDistinctSymbols();
        if (symbols.isEmpty()) {
            log.info("No held symbols to refresh");
            return;
        }
        log.info("Scheduled quote refresh starting for {} symbol(s): {}", symbols.size(), symbols);
        for (String symbol : symbols) {
            try {
                marketDataService.fetchAndSave(symbol);
                log.info("Refreshed quote for {}", symbol);
            } catch (Exception e) {
                log.error("Failed to refresh quote for {}: {}", symbol, e.getMessage());
            }
        }
        log.info("Scheduled quote refresh complete");
    }

    @Scheduled(cron = "0 5 16 * * MON-FRI", zone = "America/New_York")
    public void takeEndOfDaySnapshots() {
        log.info("Taking end-of-day portfolio snapshots");
        snapshotService.takeSnapshotsForAllUsers();
    }
}
