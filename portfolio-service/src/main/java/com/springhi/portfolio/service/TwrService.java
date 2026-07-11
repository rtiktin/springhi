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
import java.time.LocalTime;
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
        LocalDate startDate = resolveStartDate(range);
        List<PortfolioSnapshot> snapshots =
                snapshotRepository.findByPortfolioIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                        portfolioId, startDate);

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

            LocalDateTime afterTs = s1.getSnapshotDate().atTime(LocalTime.MAX);
            LocalDateTime beforeTs = s2.getSnapshotDate().atTime(LocalTime.MAX);
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
                    s1.getSnapshotDate(),
                    s2.getSnapshotDate(),
                    beginValue,
                    endValue,
                    netCashFlow,
                    periodReturn * 100.0
            ));
        }

        PortfolioSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
        BigDecimal liveValue = snapshotService.computeLiveValue(portfolioId);
        LocalDate today = LocalDate.now();

        if (liveValue != null && !today.equals(lastSnapshot.getSnapshotDate())) {
            BigDecimal beginValue = lastSnapshot.getTotalValue();
            LocalDateTime afterTs = lastSnapshot.getSnapshotDate().atTime(LocalTime.MAX);
            LocalDateTime beforeTs = today.atTime(LocalTime.MAX);
            List<Transaction> cashFlows = transactionRepository.findCashFlowsBetween(portfolioId, afterTs, beforeTs);

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

            subPeriods.add(new TwrSubPeriodDto(
                    lastSnapshot.getSnapshotDate(),
                    today,
                    beginValue,
                    liveValue,
                    netCashFlow,
                    periodReturn * 100.0
            ));
        }

        double twrPercent = (chainedReturn - 1.0) * 100.0;
        LocalDate effectiveStart = snapshots.get(0).getSnapshotDate();
        LocalDate effectiveEnd = subPeriods.isEmpty() ? lastSnapshot.getSnapshotDate() : today;

        return new TwrResponseDto(twrPercent, effectiveStart, effectiveEnd, snapshots.size() + (subPeriods.size() > snapshots.size() - 1 ? 1 : 0), subPeriods);
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
