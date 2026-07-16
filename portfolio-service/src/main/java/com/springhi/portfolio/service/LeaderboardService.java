package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.LeaderboardEntryDto;
import com.springhi.portfolio.dto.TwrResponseDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final int MIN_HOLDINGS = 5;
    private static final double MAX_SINGLE_HOLDING_PCT = 40.0;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;
    private final TwrService twrService;
    private final UserServiceClient userServiceClient;
    private final SpyBenchmarkService spyBenchmarkService;

    public LeaderboardService(PortfolioRepository portfolioRepository,
                              PortfolioService portfolioService,
                              TwrService twrService,
                              UserServiceClient userServiceClient,
                              SpyBenchmarkService spyBenchmarkService) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.twrService = twrService;
        this.userServiceClient = userServiceClient;
        this.spyBenchmarkService = spyBenchmarkService;
    }

    public List<LeaderboardEntryDto> getLeaderboard(String range, String scope, Long userId, String jwtToken) {
        List<Portfolio> portfolios;
        if ("mine".equalsIgnoreCase(scope)) {
            portfolios = portfolioRepository.findByUserIdOrderByCreatedAtAsc(userId);
        } else {
            portfolios = portfolioRepository.findAll();
        }

        Map<Long, String> usernameMap = java.util.Collections.emptyMap();
        if (!"mine".equalsIgnoreCase(scope)) {
            List<Long> userIds = portfolios.stream()
                    .map(Portfolio::getUserId).distinct().collect(Collectors.toList());
            usernameMap = userServiceClient.getDisplayNames(userIds, jwtToken);
        }

        Double spyReturn = getSpyReturnForRange(range);
        return buildEntries(portfolios, range, usernameMap, !"mine".equalsIgnoreCase(scope), spyReturn, false);
    }

    public List<LeaderboardEntryDto> getMonthlyLeaderboard(String monthStr, String jwtToken) {
        LocalDate competitionMonth = LocalDate.parse(monthStr + "-01");
        LocalDate creationMonthStart = competitionMonth.minusMonths(1);
        java.time.LocalDateTime from = creationMonthStart.atStartOfDay();
        java.time.LocalDateTime to = competitionMonth.atStartOfDay();
        List<Portfolio> portfolios = portfolioRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);

        List<Long> userIds = portfolios.stream()
                .map(Portfolio::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> usernameMap = userServiceClient.getDisplayNames(userIds, jwtToken);

        LocalDate anchor = competitionMonth;
        LocalDate today = LocalDate.now();
        long yearsElapsed = java.time.temporal.ChronoUnit.YEARS.between(competitionMonth, today);
        anchor = competitionMonth.plusYears(yearsElapsed);

        Double spyReturn = spyBenchmarkService.getSpyReturn(anchor);

        final LocalDate finalAnchor = anchor;
        List<LeaderboardEntryDto> entries = new ArrayList<>();
        for (Portfolio portfolio : portfolios) {
            try {
                List<AssetWithPrice> holdings = portfolioService.getUserAssetsWithPrices(portfolio.getId());
                if (holdings.size() < MIN_HOLDINGS) continue;

                BigDecimal totalMarketValue = holdings.stream()
                        .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalMarketValue.compareTo(BigDecimal.ZERO) == 0) continue;

                double maxPct = holdings.stream()
                        .mapToDouble(h -> h.getMarketValue() == null ? 0.0
                                : h.getMarketValue().doubleValue() / totalMarketValue.doubleValue() * 100.0)
                        .max().orElse(0.0);
                if (maxPct > MAX_SINGLE_HOLDING_PCT) continue;

                TwrResponseDto twr = twrService.computeTwr(portfolio.getId(), null, finalAnchor);
                if (twr.snapshotCount() < 2) continue;

                String username = usernameMap.getOrDefault(portfolio.getUserId(), "user-" + portfolio.getUserId());
                Double margin = spyReturn != null ? twr.twrPercent() - spyReturn : null;

                entries.add(new LeaderboardEntryDto(0, portfolio.getId(), portfolio.getName(),
                        username, twr.twrPercent(), margin, holdings.size(),
                        Math.round(maxPct * 10.0) / 10.0, competitionMonth));
            } catch (Exception e) {
                log.warn("Skipping portfolio {} for monthly leaderboard: {}", portfolio.getId(), e.getMessage());
            }
        }

        entries.sort(Comparator.comparingDouble(LeaderboardEntryDto::twrPercent).reversed());
        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntryDto e = entries.get(i);
            ranked.add(new LeaderboardEntryDto(i + 1, e.portfolioId(), e.portfolioName(),
                    e.username(), e.twrPercent(), e.marginVsSpy(), e.holdingCount(), e.maxHoldingPct(), e.competitionMonth()));
        }
        return ranked;
    }

    private Double getSpyReturnForRange(String range) {
        try {
            Map<String, Double> returns = spyBenchmarkService.getSpyReturns();
            String key = range != null ? range.toUpperCase() : "ALL";
            return returns.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private List<LeaderboardEntryDto> buildEntries(List<Portfolio> portfolios, String range,
                                                   Map<Long, String> usernameMap, boolean includeUsername,
                                                   Double spyReturn, boolean competitionOnly) {
        List<LeaderboardEntryDto> entries = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            if (competitionOnly && portfolio.getCompetitionMonth() == null) continue;
            try {
                List<AssetWithPrice> holdings = portfolioService.getUserAssetsWithPrices(portfolio.getId());

                if (holdings.size() < MIN_HOLDINGS) continue;

                BigDecimal totalMarketValue = holdings.stream()
                        .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalMarketValue.compareTo(BigDecimal.ZERO) == 0) continue;

                double maxPct = holdings.stream()
                        .mapToDouble(h -> h.getMarketValue() == null ? 0.0
                                : h.getMarketValue().doubleValue() / totalMarketValue.doubleValue() * 100.0)
                        .max()
                        .orElse(0.0);

                if (maxPct > MAX_SINGLE_HOLDING_PCT) continue;

                TwrResponseDto twr = twrService.computeTwr(portfolio.getId(), range);
                if (twr.snapshotCount() < 2) continue;

                String username = includeUsername
                        ? usernameMap.getOrDefault(portfolio.getUserId(), "user-" + portfolio.getUserId())
                        : null;

                Double margin = spyReturn != null ? twr.twrPercent() - spyReturn : null;

                entries.add(new LeaderboardEntryDto(
                        0,
                        portfolio.getId(),
                        portfolio.getName(),
                        username,
                        twr.twrPercent(),
                        margin,
                        holdings.size(),
                        Math.round(maxPct * 10.0) / 10.0,
                        portfolio.getCompetitionMonth()
                ));
            } catch (Exception e) {
                log.warn("Skipping portfolio {} for leaderboard: {}", portfolio.getId(), e.getMessage());
            }
        }

        entries.sort(Comparator.comparingDouble(LeaderboardEntryDto::twrPercent).reversed());

        List<LeaderboardEntryDto> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntryDto e = entries.get(i);
            ranked.add(new LeaderboardEntryDto(i + 1, e.portfolioId(), e.portfolioName(),
                    e.username(), e.twrPercent(), e.marginVsSpy(), e.holdingCount(), e.maxHoldingPct(), e.competitionMonth()));
        }
        return ranked;
    }
}
