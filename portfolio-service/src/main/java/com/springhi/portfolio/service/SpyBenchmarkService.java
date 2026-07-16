package com.springhi.portfolio.service;

import com.springhi.portfolio.model.MarketQuote;
import com.springhi.portfolio.repository.MarketQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class SpyBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(SpyBenchmarkService.class);
    private static final String SPY = "SPY";
    private static final String QUOTE_TYPE = "REALTIME";

    private final MarketQuoteRepository marketQuoteRepository;
    private final MarketDataService marketDataService;

    public SpyBenchmarkService(MarketQuoteRepository marketQuoteRepository,
                               MarketDataService marketDataService) {
        this.marketQuoteRepository = marketQuoteRepository;
        this.marketDataService = marketDataService;
    }

    public void refreshSpyPrice() {
        try {
            marketDataService.fetchAndSave(SPY);
            log.info("SPY price refreshed");
        } catch (Exception e) {
            log.error("Failed to refresh SPY price: {}", e.getMessage());
        }
    }

    public void seedHistoricalSpyPrices() {
        try {
            log.info("Seeding historical SPY prices from Yahoo Finance (2y)");
            java.util.Map<LocalDate, java.math.BigDecimal> bars = fetchYahooHistoricalCloses(SPY, "2y");
            int saved = 0;
            for (java.util.Map.Entry<LocalDate, java.math.BigDecimal> entry : bars.entrySet()) {
                LocalDate day = entry.getKey();
                java.math.BigDecimal price = entry.getValue();
                boolean exists = marketQuoteRepository.findBySymbolAndQuoteTypeAndTradingDay(SPY, QUOTE_TYPE, day).isPresent();
                if (!exists) {
                    com.springhi.portfolio.model.MarketQuote quote = new com.springhi.portfolio.model.MarketQuote();
                    quote.setSymbol(SPY);
                    quote.setQuoteType(QUOTE_TYPE);
                    quote.setTradingDay(day);
                    quote.setPrice(price);
                    marketQuoteRepository.save(quote);
                    saved++;
                }
            }
            log.info("Historical SPY seed complete: {} new rows inserted (out of {} bars)", saved, bars.size());
        } catch (Exception e) {
            log.error("Failed to seed historical SPY prices: {}", e.getMessage(), e);
        }
    }

    private java.util.Map<LocalDate, java.math.BigDecimal> fetchYahooHistoricalCloses(String symbol, String range) {
        java.util.Map<LocalDate, java.math.BigDecimal> result = new java.util.LinkedHashMap<>();
        try {
            String urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/"
                    + symbol.toUpperCase() + "?interval=1d&range=" + range;
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != 200) {
                log.warn("Yahoo Finance historical bars returned HTTP {} for {}", conn.getResponseCode(), symbol);
                return result;
            }
            String body;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                body = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            com.fasterxml.jackson.databind.JsonNode resultNode = root.path("chart").path("result").path(0);
            com.fasterxml.jackson.databind.JsonNode timestamps = resultNode.path("timestamp");
            com.fasterxml.jackson.databind.JsonNode closes = resultNode.path("indicators").path("quote").path(0).path("close");
            for (int i = 0; i < timestamps.size(); i++) {
                long epochSec = timestamps.get(i).asLong();
                com.fasterxml.jackson.databind.JsonNode closeNode = closes.get(i);
                if (closeNode == null || closeNode.isNull()) continue;
                double close = closeNode.asDouble();
                if (close <= 0) continue;
                LocalDate date = java.time.Instant.ofEpochSecond(epochSec)
                        .atZone(java.time.ZoneId.of("America/New_York")).toLocalDate();
                result.put(date, java.math.BigDecimal.valueOf(close).setScale(4, java.math.RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            log.error("Failed to fetch Yahoo Finance historical bars for {}: {}", symbol, e.getMessage());
        }
        return result;
    }

    public Map<String, Double> getSpyReturns() {
        Optional<MarketQuote> currentOpt = marketQuoteRepository
                .findTopBySymbolAndQuoteTypeOrderByFetchedAtDesc(SPY, QUOTE_TYPE);

        if (currentOpt.isEmpty() || currentOpt.get().getPrice() == null) {
            return Map.of();
        }

        BigDecimal currentPrice = currentOpt.get().getPrice();
        LocalDate today = LocalDate.now();

        Map<String, LocalDate> ranges = new LinkedHashMap<>();
        ranges.put("1W", today.minusWeeks(1));
        ranges.put("1M", today.minusMonths(1));
        ranges.put("3M", today.minusMonths(3));
        ranges.put("6M", today.minusMonths(6));
        ranges.put("1Y", today.minusYears(1));

        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, LocalDate> entry : ranges.entrySet()) {
            Optional<MarketQuote> pastOpt = marketQuoteRepository
                    .findTopBySymbolAndQuoteTypeAndTradingDayLessThanEqualOrderByTradingDayDesc(
                            SPY, QUOTE_TYPE, entry.getValue());
            if (pastOpt.isPresent() && pastOpt.get().getPrice() != null) {
                double past = pastOpt.get().getPrice().doubleValue();
                double current = currentPrice.doubleValue();
                if (past > 0) {
                    result.put(entry.getKey(), (current - past) / past * 100.0);
                }
            }
        }
        return result;
    }

    public Double getSpyReturn(LocalDate startDate) {
        Optional<MarketQuote> currentOpt = marketQuoteRepository
                .findTopBySymbolAndQuoteTypeOrderByFetchedAtDesc(SPY, QUOTE_TYPE);
        if (currentOpt.isEmpty() || currentOpt.get().getPrice() == null) return null;

        Optional<MarketQuote> pastOpt = marketQuoteRepository
                .findTopBySymbolAndQuoteTypeAndTradingDayLessThanEqualOrderByTradingDayDesc(
                        SPY, QUOTE_TYPE, startDate);
        if (pastOpt.isEmpty() || pastOpt.get().getPrice() == null) return null;

        double past = pastOpt.get().getPrice().doubleValue();
        double current = currentOpt.get().getPrice().doubleValue();
        return past > 0 ? (current - past) / past * 100.0 : null;
    }
}
