package com.springhi.portfolio.service;

import com.springhi.portfolio.model.DividendEvent;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.repository.DividendEventRepository;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class DividendService {

    private static final Logger log = LoggerFactory.getLogger(DividendService.class);
    private static final int MAX_LOOKBACK_DAYS = 90;

    private final AlpacaService alpacaService;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final DividendEventRepository dividendEventRepository;

    public DividendService(AlpacaService alpacaService,
                           PortfolioRepository portfolioRepository,
                           TransactionRepository transactionRepository,
                           DividendEventRepository dividendEventRepository) {
        this.alpacaService = alpacaService;
        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.dividendEventRepository = dividendEventRepository;
    }

    public void processAllPortfolios() {
        List<Portfolio> portfolios = portfolioRepository.findAll();
        for (Portfolio portfolio : portfolios) {
            try {
                processPortfolio(portfolio);
            } catch (Exception e) {
                log.error("Dividend processing failed for portfolioId={}: {}", portfolio.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processPortfolio(Portfolio portfolio) {
        Long portfolioId = portfolio.getId();

        LocalDate lastProcessed = dividendEventRepository
                .findLastProcessedExDate(portfolioId)
                .orElse(null);

        LocalDate from;
        if (lastProcessed != null) {
            from = lastProcessed.plusDays(1);
        } else {
            LocalDate createdDate = portfolio.getCreatedAt() != null
                    ? portfolio.getCreatedAt().toLocalDate()
                    : LocalDate.now().minusDays(MAX_LOOKBACK_DAYS);
            from = createdDate;
        }

        LocalDate today = LocalDate.now();
        LocalDate cappedFrom = from.isBefore(today.minusDays(MAX_LOOKBACK_DAYS))
                ? today.minusDays(MAX_LOOKBACK_DAYS)
                : from;

        if (cappedFrom.isAfter(today)) {
            return;
        }

        List<String> symbols = transactionRepository.findDistinctBoughtSymbols(portfolioId);
        if (symbols.isEmpty()) {
            return;
        }

        log.info("Processing dividends for portfolioId={} symbols={} from={} to={}",
                portfolioId, symbols, cappedFrom, today);

        for (String symbol : symbols) {
            processDividendsForSymbol(portfolioId, portfolio.getUserId(), symbol, cappedFrom, today);
        }
    }

    private void processDividendsForSymbol(Long portfolioId, Long userId,
                                           String symbol, LocalDate from, LocalDate to) {
        List<AlpacaService.DividendInfo> dividends = alpacaService.fetchDividends(symbol, from, to);

        for (AlpacaService.DividendInfo div : dividends) {
            LocalDate exDate = div.exDate();

            if (dividendEventRepository.existsByPortfolioIdAndSymbolAndExDate(portfolioId, symbol, exDate)) {
                log.debug("Dividend already recorded: portfolioId={} symbol={} exDate={}", portfolioId, symbol, exDate);
                continue;
            }

            BigDecimal sharesHeld = computeSharesHeldOnDate(portfolioId, symbol, exDate);
            if (sharesHeld.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No shares held on exDate={} for portfolioId={} symbol={}", exDate, portfolioId, symbol);
                continue;
            }

            BigDecimal totalCredited = sharesHeld.multiply(div.amount()).setScale(4, RoundingMode.HALF_UP);

            Transaction t = new Transaction();
            t.setPortfolioId(portfolioId);
            t.setUserId(userId);
            t.setSymbol(symbol);
            t.setType("DIVIDEND");
            t.setQuantity(sharesHeld);
            t.setPrice(div.amount());
            t.setTimestamp(exDate.atTime(LocalTime.NOON));
            transactionRepository.save(t);

            DividendEvent event = new DividendEvent();
            event.setPortfolioId(portfolioId);
            event.setSymbol(symbol);
            event.setExDate(exDate);
            event.setDividendPerShare(div.amount());
            event.setSharesHeld(sharesHeld);
            event.setTotalCredited(totalCredited);
            dividendEventRepository.save(event);

            log.info("Dividend credited: portfolioId={} symbol={} exDate={} shares={} perShare={} total={}",
                    portfolioId, symbol, exDate, sharesHeld, div.amount(), totalCredited);
        }
    }

    private BigDecimal computeSharesHeldOnDate(Long portfolioId, String symbol, LocalDate date) {
        LocalDateTime asOf = date.atTime(LocalTime.MAX);
        List<Transaction> trades = transactionRepository.findTradesForSymbolUpTo(portfolioId, symbol, asOf);

        BigDecimal net = BigDecimal.ZERO;
        for (Transaction t : trades) {
            if ("BUY".equals(t.getType())) {
                net = net.add(t.getQuantity());
            } else if ("SELL".equals(t.getType())) {
                net = net.subtract(t.getQuantity());
            }
        }
        return net.max(BigDecimal.ZERO);
    }
}
