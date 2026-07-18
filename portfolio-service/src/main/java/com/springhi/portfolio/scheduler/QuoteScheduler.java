package com.springhi.portfolio.scheduler;

import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.repository.MarketQuoteRepository;
import com.springhi.portfolio.service.MarketDataService;
import com.springhi.portfolio.service.PortfolioSnapshotService;
import com.springhi.portfolio.service.SnapshotTracker;
import com.springhi.portfolio.service.SpyBenchmarkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class QuoteScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuoteScheduler.class);

    private static final LocalTime SNAPSHOT_TIME  = LocalTime.of(16, 5);
    private static final LocalTime MARKET_OPEN    = LocalTime.of(9, 0);
    private static final ZoneId    NY             = ZoneId.of("America/New_York");

    private final AssetRepository assetRepository;
    private final MarketDataService marketDataService;
    private final PortfolioSnapshotService snapshotService;
    private final SpyBenchmarkService spyBenchmarkService;
    private final MarketQuoteRepository marketQuoteRepository;
    private final SnapshotTracker snapshotTracker;

    public QuoteScheduler(AssetRepository assetRepository,
                          MarketDataService marketDataService,
                          PortfolioSnapshotService snapshotService,
                          SpyBenchmarkService spyBenchmarkService,
                          MarketQuoteRepository marketQuoteRepository,
                          SnapshotTracker snapshotTracker) {
        this.assetRepository = assetRepository;
        this.marketDataService = marketDataService;
        this.snapshotService = snapshotService;
        this.spyBenchmarkService = spyBenchmarkService;
        this.marketQuoteRepository = marketQuoteRepository;
        this.snapshotTracker = snapshotTracker;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        log.info("Startup catch-up check triggered");
        spyBenchmarkService.seedHistoricalSpyPrices();

        LocalTime now = LocalTime.now(NY);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        if (now.isAfter(SNAPSHOT_TIME)) {
            // After 4:05 PM — closing prices should be in the DB.
            // Take today's snapshot if the primary job was missed.
            if (snapshotTracker.tryStart()) {
                try {
                    log.info("Startup after 4:05 PM — checking for missed today snapshot");
                    snapshotService.takeSnapshotsIfNotTakenToday();
                } finally {
                    snapshotTracker.finish();
                }
            }
            spyBenchmarkService.refreshSpyPrice();

        } else {
            // Before 4:05 PM — the primary snapshot job has not fired yet for today.

            // Step 1: If yesterday's prices are still the latest in the DB
            // (i.e. today's prices haven't been fetched yet), take yesterday's
            // snapshot now while those prices are still accurate.
            boolean todayPricesPresent = marketQuoteRepository.existsByTradingDay(today);
            if (!todayPricesPresent && snapshotTracker.tryStart()) {
                try {
                    log.info("Startup before 4:05 PM, today's prices not yet fetched — taking yesterday's catch-up snapshot");
                    snapshotService.takeSnapshotsIfNotTakenOnDate(yesterday);
                } finally {
                    snapshotTracker.finish();
                }
            }

            // Step 2: If it's after 9:00 AM, refresh prices so the rest of the
            // day uses current market data.
            if (now.isAfter(MARKET_OPEN)) {
                log.info("Startup after 9:00 AM — refreshing prices");
                refreshAllHeldSymbols();
                spyBenchmarkService.refreshSpyPrice();
            }
        }
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

    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "America/New_York")
    public void refreshSpyEndOfDay() {
        log.info("End-of-day SPY price refresh");
        spyBenchmarkService.refreshSpyPrice();
    }

    @Scheduled(cron = "0 5 16 * * MON-FRI", zone = "America/New_York")
    public void takeEndOfDaySnapshots() {
        if (!snapshotTracker.tryStart()) {
            log.info("Snapshot already running — skipping end-of-day job");
            return;
        }
        try {
            log.info("Taking end-of-day portfolio snapshots");
            snapshotService.takeSnapshotsForAllUsers();
        } finally {
            snapshotTracker.finish();
        }
    }

    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "America/New_York")
    public void backupSnapshotCheck() {
        if (!snapshotTracker.tryStart()) {
            log.info("Snapshot already running — skipping backup check");
            return;
        }
        try {
            log.info("Backup snapshot check: ensuring all portfolios have a snapshot for today");
            snapshotService.takeSnapshotsIfNotTakenToday();
        } finally {
            snapshotTracker.finish();
        }
    }
}
