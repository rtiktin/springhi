package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.QuoteResponse;
import com.springhi.portfolio.model.MarketQuote;
import com.springhi.portfolio.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<QuoteResponse> getQuote(@PathVariable String symbol) {
        return marketDataService.getQuote(symbol.toUpperCase())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history/{symbol}")
    public ResponseEntity<List<QuoteResponse>> getHistory(@PathVariable String symbol) {
        List<QuoteResponse> history = marketDataService.getQuoteHistory(symbol.toUpperCase())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    private QuoteResponse toResponse(MarketQuote q) {
        return new QuoteResponse(
                q.getSymbol(),
                q.getPrice(),
                q.getOpen(),
                q.getHigh(),
                q.getLow(),
                q.getVolume(),
                q.getPreviousClose(),
                q.getChange(),
                q.getChangePercent(),
                q.getTradingDay(),
                q.getFetchedAt()
        );
    }
}
