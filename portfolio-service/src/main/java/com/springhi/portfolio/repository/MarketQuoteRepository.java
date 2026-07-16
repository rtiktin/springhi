package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.MarketQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketQuoteRepository extends JpaRepository<MarketQuote, Long> {

    Optional<MarketQuote> findTopBySymbolAndQuoteTypeOrderByFetchedAtDesc(String symbol, String quoteType);

    Optional<MarketQuote> findBySymbolAndQuoteTypeAndTradingDay(String symbol, String quoteType, LocalDate tradingDay);

    List<MarketQuote> findBySymbolAndQuoteTypeOrderByFetchedAtDesc(String symbol, String quoteType);

    Optional<MarketQuote> findTopBySymbolAndQuoteTypeAndTradingDayLessThanEqualOrderByTradingDayDesc(
            String symbol, String quoteType, LocalDate date);
}
