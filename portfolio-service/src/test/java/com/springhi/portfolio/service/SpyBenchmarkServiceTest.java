package com.springhi.portfolio.service;

import com.springhi.portfolio.model.MarketQuote;
import com.springhi.portfolio.repository.MarketQuoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpyBenchmarkServiceTest {
    private final MarketQuoteRepository quotes = mock(MarketQuoteRepository.class);
    private final SpyBenchmarkService service = new SpyBenchmarkService(quotes, mock(MarketDataService.class));

    @Test
    void comparesPricesAtPortfolioTwrStartAndEndDates() {
        LocalDate start = LocalDate.of(2026, 7, 15);
        LocalDate end = LocalDate.of(2026, 7, 20);
        MarketQuote startQuote = new MarketQuote();
        startQuote.setPrice(new BigDecimal("100"));
        MarketQuote endQuote = new MarketQuote();
        endQuote.setPrice(new BigDecimal("105"));
        when(quotes.findTopBySymbolAndQuoteTypeAndTradingDayLessThanEqualOrderByTradingDayDesc("SPY", "REALTIME", start))
                .thenReturn(Optional.of(startQuote));
        when(quotes.findTopBySymbolAndQuoteTypeAndTradingDayLessThanEqualOrderByTradingDayDesc("SPY", "REALTIME", end))
                .thenReturn(Optional.of(endQuote));

        assertEquals(5.0, service.getSpyReturn(start, end));
    }

    @Test
    void omitsComparisonWhenHistoricalPriceIsUnavailable() {
        LocalDate start = LocalDate.of(2026, 7, 15);
        LocalDate end = LocalDate.of(2026, 7, 20);
        assertNull(service.getSpyReturn(start, end));
    }
}
