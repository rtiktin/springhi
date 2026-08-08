package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.model.OptimizationSchedule;
import com.springhi.portfolio.model.PortfolioRecommendation;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.repository.OptimizationScheduleRepository;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class ScheduledOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledOptimizationService.class);
    private static final LocalTime RUN_TIME = LocalTime.of(10, 30);

    private final OptimizationScheduleRepository scheduleRepository;
    private final PortfolioOptimizationService optimizationService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;

    public ScheduledOptimizationService(OptimizationScheduleRepository scheduleRepository,
                                        PortfolioOptimizationService optimizationService,
                                        PortfolioRecommendationRepository recommendationRepository,
                                        PortfolioService portfolioService,
                                        MarketDataService marketDataService) {
        this.scheduleRepository = scheduleRepository;
        this.optimizationService = optimizationService;
        this.recommendationRepository = recommendationRepository;
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
    }

    public void processDueSchedules() {
        List<OptimizationSchedule> due = scheduleRepository.findDueSchedules(LocalDateTime.now());
        if (due.isEmpty()) return;
        log.info("Processing {} due optimization schedule(s)", due.size());
        for (OptimizationSchedule schedule : due) {
            try {
                runSchedule(schedule);
            } catch (Exception e) {
                log.error("Scheduled optimization failed for scheduleId={} portfolioId={}: {}",
                        schedule.getId(), schedule.getPortfolioId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void runSchedule(OptimizationSchedule schedule) {
        log.info("Running scheduled optimization: scheduleId={} portfolioId={} provider={}",
                schedule.getId(), schedule.getPortfolioId(), schedule.getAiProvider());

        OptimizationResponse response = optimizationService.optimize(
                schedule.getUserId(), schedule.getPortfolioId(), schedule.getAiProvider());

        List<RecommendationDto> recs = response.recommendations();
        if (recs == null || recs.isEmpty()) {
            log.warn("No recommendations generated for scheduleId={}", schedule.getId());
        } else {
            stampScheduleId(recs, schedule.getId());
            log.info("Auto-executing {} recommendation(s) for scheduleId={}", recs.size(), schedule.getId());
            autoExecuteRecommendations(schedule.getPortfolioId(), schedule.getUserId(), recs);
        }

        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setNextRunAt(computeNextRunAt(schedule));
        scheduleRepository.save(schedule);

        log.info("Schedule updated: nextRunAt={}", schedule.getNextRunAt());
    }

    private void stampScheduleId(List<RecommendationDto> recs, Long scheduleId) {
        for (RecommendationDto rec : recs) {
            recommendationRepository.findById(rec.id()).ifPresent(r -> {
                r.setScheduleId(scheduleId);
                recommendationRepository.save(r);
            });
        }
    }

    private void autoExecuteRecommendations(Long portfolioId, Long userId, List<RecommendationDto> recs) {
        List<RecommendationDto> sells = recs.stream().filter(r -> "SELL".equals(r.action())).toList();
        List<RecommendationDto> buys  = recs.stream().filter(r -> "BUY".equals(r.action())).toList();

        for (RecommendationDto rec : sells) {
            executeRecommendation(portfolioId, userId, rec);
        }
        for (RecommendationDto rec : buys) {
            executeRecommendation(portfolioId, userId, rec);
        }
    }

    private void executeRecommendation(Long portfolioId, Long userId, RecommendationDto rec) {
        try {
            String symbol = rec.t();
            String action = rec.action();

            BigDecimal price = marketDataService.getLatestCachedQuote(symbol)
                    .map(q -> q.getPrice())
                    .orElse(null);

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Skipping {} {} — no price available", action, symbol);
                markSkipped(rec.id());
                return;
            }

            BigDecimal quantity;
            if ("SELL".equals(action)) {
                quantity = portfolioService.getUserAssets(portfolioId).stream()
                        .filter(a -> a.getSymbol().equals(symbol))
                        .map(a -> a.getQuantity())
                        .findFirst()
                        .orElse(BigDecimal.ZERO);
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Skipping SELL {} — no shares held", symbol);
                    markSkipped(rec.id());
                    return;
                }
            } else {
                BigDecimal cashBalance = portfolioService.getCashBalance(portfolioId);
                BigDecimal estimatedValue = rec.estimatedValue() != null ? rec.estimatedValue() : BigDecimal.ZERO;
                if (estimatedValue.compareTo(BigDecimal.ZERO) <= 0 || cashBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("Skipping BUY {} — insufficient cash or zero estimated value", symbol);
                    markSkipped(rec.id());
                    return;
                }
                BigDecimal spendable = estimatedValue.min(cashBalance);
                quantity = spendable.divide(price, 4, java.math.RoundingMode.HALF_UP);
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    markSkipped(rec.id());
                    return;
                }
            }

            Transaction t = new Transaction();
            t.setPortfolioId(portfolioId);
            t.setUserId(userId);
            t.setSymbol(symbol);
            t.setType(action);
            t.setQuantity(quantity);
            t.setPrice(price);
            Transaction saved = portfolioService.processTransaction(t);

            recommendationRepository.findById(rec.id()).ifPresent(r -> {
                r.setStatus("EXECUTED");
                r.setTransactionId(saved.getId());
                r.setEstimatedValue(quantity.multiply(price));
                r.setExecutedAt(LocalDateTime.now());
                recommendationRepository.save(r);
            });

            log.info("Auto-executed {} {} qty={} price={} portfolioId={}", action, symbol, quantity, price, portfolioId);

        } catch (Exception e) {
            log.error("Failed to auto-execute {} {}: {}", rec.action(), rec.t(), e.getMessage(), e);
            markSkipped(rec.id());
        }
    }

    private void markSkipped(Long recId) {
        recommendationRepository.findById(recId).ifPresent(r -> {
            r.setStatus("SKIPPED");
            recommendationRepository.save(r);
        });
    }

    public OptimizationSchedule createSchedule(Long portfolioId, Long userId,
                                               String frequency, String aiProvider,
                                               Integer dayOfWeek, Integer dayOfMonth) {
        OptimizationSchedule schedule = new OptimizationSchedule();
        schedule.setPortfolioId(portfolioId);
        schedule.setUserId(userId);
        schedule.setFrequency(frequency);
        schedule.setAiProvider(aiProvider);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setDayOfMonth(dayOfMonth);
        schedule.setEnabled(true);
        schedule.setNextRunAt(computeNextRunAt(schedule));
        return scheduleRepository.save(schedule);
    }

    public OptimizationSchedule updateSchedule(OptimizationSchedule schedule, String frequency,
                                               String aiProvider, Integer dayOfWeek,
                                               Integer dayOfMonth, boolean enabled) {
        schedule.setFrequency(frequency);
        schedule.setAiProvider(aiProvider);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setDayOfMonth(dayOfMonth);
        schedule.setEnabled(enabled);
        schedule.setNextRunAt(enabled ? computeNextRunAt(schedule) : null);
        return scheduleRepository.save(schedule);
    }

    public LocalDateTime computeNextRunAt(OptimizationSchedule schedule) {
        LocalDate today = LocalDate.now();
        LocalDate next;
        switch (schedule.getFrequency().toUpperCase()) {
            case "DAILY" -> next = today.plusDays(1);
            case "WEEKLY" -> {
                int target = schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : 1;
                DayOfWeek dow = DayOfWeek.of(target);
                next = today.with(TemporalAdjusters.nextOrSame(dow));
                if (!next.isAfter(today)) next = next.plusWeeks(1);
            }
            case "MONTHLY" -> {
                int dom = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                next = today.withDayOfMonth(Math.min(dom, today.lengthOfMonth()));
                if (!next.isAfter(today)) next = next.plusMonths(1)
                        .withDayOfMonth(Math.min(dom, next.plusMonths(1).lengthOfMonth()));
            }
            case "QUARTERLY" -> {
                int dom = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                next = today.withDayOfMonth(Math.min(dom, today.lengthOfMonth()));
                if (!next.isAfter(today)) {
                    next = next.plusMonths(3);
                    next = next.withDayOfMonth(Math.min(dom, next.lengthOfMonth()));
                }
            }
            case "YEARLY" -> {
                int dom = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1;
                next = today.withDayOfYear(1).withDayOfMonth(Math.min(dom, today.lengthOfMonth()));
                if (!next.isAfter(today)) next = next.plusYears(1)
                        .withDayOfMonth(Math.min(dom, next.plusYears(1).lengthOfMonth()));
            }
            default -> next = today.plusDays(1);
        }
        return next.atTime(RUN_TIME);
    }
}
