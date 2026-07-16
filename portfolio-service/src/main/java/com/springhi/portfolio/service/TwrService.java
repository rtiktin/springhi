package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.TwrResponseDto;
import com.springhi.portfolio.dto.TwrSubPeriodDto;
import com.springhi.portfolio.model.PortfolioSnapshot;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.repository.PortfolioSnapshotRepository;
import com.springhi.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TwrService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final TransactionRepository transactionRepository;
    private final PortfolioSnapshotService snapshotService;

    public TwrService(PortfolioSnapshotRepository snapshotRepository,
                      TransactionRepository transactionRepository,
                      PortfolioSnapshotService snapshotService) {
        this.snapshotRepository = snapshotRepository;
        this.transactionRepository = transactionRepository;
        this.snapshotService = snapshotService;
    }

    public TwrResponseDto computeTwr(Long portfolioId, String range) {
        return computeTwr(portfolioId, range, null);
    }

    public TwrResponseDto computeTwr(Long portfolioId, String range, LocalDate anchorDate) {
        LocalDate startDate = anchorDate != null ? anchorDate : resolveStartDate(range);
        LocalDateTime startAt = startDate.atStartOfDay();
        List<PortfolioSnapshot> snapshots =
                snapshotRepository.findByPortfolioIdAndSnapshotAtGreaterThanEqualOrderBySnapshotAtAsc(
                        portfolioId, startAt);

        if (snapshots.size() < 2) {
            return new TwrResponseDto(0.0, startDate, LocalDate.now(), snapshots.size(), List.of());
        }

        List<TwrSubPeriodDto> subPeriods = new ArrayList<>();
        double chainedReturn = 1.0;

        for (int i = 0; i < snapshots.size() - 1; i++) {
            PortfolioSnapshot s1 = snapshots.get(i);
            PortfolioSnapshot s2 = snapshots.get(i + 1);

            BigDecimal beginValue = s1.getTotalValue();
            BigDecimal endValue = s2.getTotalValue();

            LocalDateTime afterTs = s1.getSnapshotAt();
            LocalDateTime beforeTs = s2.getSnapshotAt();
            List<Transaction> cashFlows = transactionRepository.findCashFlowsBetween(portfolioId, afterTs, beforeTs);

            BigDecimal netCashFlow = cashFlows.stream()
                    .map(t -> {
                        BigDecimal amount = t.getQuantity().multiply(t.getPrice());
                        return "DEPOSIT".equals(t.getType()) ? amount : amount.negate();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double begin = beginValue.doubleValue();
            double end = endValue.doubleValue();
            double cf = netCashFlow.doubleValue();

            double denominator = begin + 0.5 * cf;
            double periodReturn = denominator != 0.0 ? (end - begin - cf) / denominator : 0.0;

            chainedReturn *= (1.0 + periodReturn);

            subPeriods.add(new TwrSubPeriodDto(
                    s1.getSnapshotAt().toLocalDate(),
                    s2.getSnapshotAt().toLocalDate(),
                    beginValue,
                    endValue,
                    netCashFlow,
                    periodReturn * 100.0
            ));
        }

        PortfolioSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
        BigDecimal liveValue = snapshotService.computeLiveValue(portfolioId);
        LocalDateTime now = LocalDateTime.now();

        if (liveValue != null && java.time.temporal.ChronoUnit.SECONDS.between(lastSnapshot.getSnapshotAt(), now) > 30) {
            BigDecimal beginValue = lastSnapshot.getTotalValue();
            LocalDateTime afterTs = lastSnapshot.getSnapshotAt();
            List<Transaction> cashFlows = transactionRepository.findCashFlowsBetween(portfolioId, afterTs, now);

            BigDecimal netCashFlow = cashFlows.stream()
                    .map(t -> {
                        BigDecimal amount = t.getQuantity().multiply(t.getPrice());
                        return "DEPOSIT".equals(t.getType()) ? amount : amount.negate();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double begin = beginValue.doubleValue();
            double end = liveValue.doubleValue();
            double cf = netCashFlow.doubleValue();
            double denominator = begin + 0.5 * cf;
            double periodReturn = denominator != 0.0 ? (end - begin - cf) / denominator : 0.0;

            chainedReturn *= (1.0 + periodReturn);

            LocalDate today = LocalDate.now();
            subPeriods.add(new TwrSubPeriodDto(
                    lastSnapshot.getSnapshotAt().toLocalDate(),
                    today,
                    beginValue,
                    liveValue,
                    netCashFlow,
                    periodReturn * 100.0
            ));
        }

        double twrPercent = (chainedReturn - 1.0) * 100.0;
        LocalDate effectiveStart = snapshots.get(0).getSnapshotAt().toLocalDate();
        LocalDate effectiveEnd = subPeriods.isEmpty() ? lastSnapshot.getSnapshotAt().toLocalDate() : LocalDate.now();

        return new TwrResponseDto(twrPercent, effectiveStart, effectiveEnd, snapshots.size() + (subPeriods.size() > snapshots.size() - 1 ? 1 : 0), subPeriods);
    }

    public LocalDate resolveCompetitionAnchor(LocalDate performanceStart) {
        LocalDate today = LocalDate.now();
        long yearsElapsed = java.time.temporal.ChronoUnit.YEARS.between(performanceStart, today);
        return performanceStart.plusYears(yearsElapsed);
    }

    private LocalDate resolveStartDate(String range) {
        LocalDate today = LocalDate.now();
        if (range == null) return today.minusYears(10);
        return switch (range.toUpperCase()) {
            case "1W"  -> today.minusWeeks(1);
            case "1M"  -> today.minusMonths(1);
            case "3M"  -> today.minusMonths(3);
            case "6M"  -> today.minusMonths(6);
            case "YTD" -> today.withDayOfYear(1);
            case "1Y"  -> today.minusYears(1);
            default    -> today.minusYears(10);
        };
    }
}
