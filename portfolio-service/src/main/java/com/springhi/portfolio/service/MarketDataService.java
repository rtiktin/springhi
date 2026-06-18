package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AlpacaSnapshotResponse;
import com.springhi.portfolio.model.MarketQuote;
import com.springhi.portfolio.repository.MarketQuoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private final MarketQuoteRepository marketQuoteRepository;
    private final AlpacaService alpacaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketDataService(MarketQuoteRepository marketQuoteRepository,
                             AlpacaService alpacaService) {
        this.marketQuoteRepository = marketQuoteRepository;
        this.alpacaService = alpacaService;
    }

    public Optional<MarketQuote> getLatestCachedQuote(String symbol) {
        return marketQuoteRepository.findTopBySymbolAndQuoteTypeOrderByFetchedAtDesc(symbol, "REALTIME");
    }

    public Optional<MarketQuote> getQuote(String symbol) {
        return fetchAndSave(symbol);
    }

    public Optional<MarketQuote> fetchAndSave(String symbol) {
        return alpacaService.fetchSnapshot(symbol)
                .map(snapshot -> save(symbol, snapshot));
    }

    public Map<String, MarketQuote> refreshQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Map.of();
        Map<String, AlpacaSnapshotResponse.Snapshot> snapshots = alpacaService.fetchSnapshots(symbols);
        java.util.Map<String, MarketQuote> result = new java.util.HashMap<>();
        snapshots.forEach((symbol, snapshot) -> result.put(symbol, save(symbol, snapshot)));
        log.info("Refreshed prices for {} symbol(s)", result.size());
        return result;
    }

    public List<MarketQuote> getQuoteHistory(String symbol) {
        return marketQuoteRepository.findBySymbolAndQuoteTypeOrderByFetchedAtDesc(symbol, "REALTIME");
    }

    private MarketQuote save(String symbol, AlpacaSnapshotResponse.Snapshot snapshot) {
        AlpacaSnapshotResponse.Bar daily = snapshot.dailyBar();
        AlpacaSnapshotResponse.Bar prevDaily = snapshot.prevDailyBar();
        AlpacaSnapshotResponse.Trade trade = snapshot.latestTrade();

        BigDecimal price = trade != null ? trade.price() : (daily != null ? daily.close() : null);
        BigDecimal prevClose = prevDaily != null ? prevDaily.close() : null;

        BigDecimal change = null;
        String changePercent = null;
        if (price != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
            change = price.subtract(prevClose).setScale(4, RoundingMode.HALF_UP);
            BigDecimal pct = change.divide(prevClose, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
            changePercent = pct.toPlainString() + "%";
        }

        LocalDate tradingDay = null;
        if (daily != null && daily.timestamp() != null) {
            try {
                tradingDay = Instant.parse(daily.timestamp()).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (Exception e) {
                log.warn("Could not parse trading day timestamp '{}' for {}", daily.timestamp(), symbol);
            }
        }

        MarketQuote quote = marketQuoteRepository
                .findBySymbolAndQuoteTypeAndTradingDay(symbol, "REALTIME", tradingDay)
                .orElse(new MarketQuote());

        quote.setSymbol(symbol);
        quote.setQuoteType("REALTIME");
        quote.setTradingDay(tradingDay);
        quote.setPrice(price);
        quote.setOpen(daily != null ? daily.open() : null);
        quote.setHigh(daily != null ? daily.high() : null);
        quote.setLow(daily != null ? daily.low() : null);
        quote.setVolume(daily != null ? daily.volume() : null);
        quote.setPreviousClose(prevClose);
        quote.setChange(change);
        quote.setChangePercent(changePercent);
        quote.setFetchedAt(LocalDateTime.now());

        try {
            quote.setRawResponse(objectMapper.writeValueAsString(snapshot));
        } catch (Exception e) {
            log.warn("Could not serialize raw Alpaca snapshot for {}", symbol);
        }

        return marketQuoteRepository.save(quote);
    }
}
