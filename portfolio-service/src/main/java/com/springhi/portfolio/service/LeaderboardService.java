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

    public LeaderboardService(PortfolioRepository portfolioRepository,
                              PortfolioService portfolioService,
                              TwrService twrService,
                              UserServiceClient userServiceClient) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.twrService = twrService;
        this.userServiceClient = userServiceClient;
    }

    public List<LeaderboardEntryDto> getLeaderboard(String range, String scope, Long userId, String jwtToken) {
        List<Portfolio> portfolios;
        if ("mine".equalsIgnoreCase(scope)) {
            portfolios = portfolioRepository.findByUserIdOrderByCreatedAtAsc(userId);
        } else {
            portfolios = portfolioRepository.findAll();
        }

        List<LeaderboardEntryDto> entries = buildEntries(portfolios, range, null);

        if (!"mine".equalsIgnoreCase(scope)) {
            List<Long> userIds = portfolios.stream()
                    .map(Portfolio::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, String> displayNames = userServiceClient.getDisplayNames(userIds, jwtToken);

            entries = entries.stream()
                    .map(e -> {
                        Portfolio p = portfolios.stream()
                                .filter(x -> x.getId().equals(e.portfolioId()))
                                .findFirst().orElse(null);
                        String name = p != null ? displayNames.getOrDefault(p.getUserId(), "User #" + p.getUserId()) : null;
                        return new LeaderboardEntryDto(e.rank(), e.portfolioId(), e.portfolioName(),
                                name, e.twrPercent(), e.holdingCount(), e.maxHoldingPct());
                    })
                    .collect(Collectors.toList());
        }

        return entries;
    }

    private List<LeaderboardEntryDto> buildEntries(List<Portfolio> portfolios, String range, Map<Long, String> displayNames) {
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
                        .max()
                        .orElse(0.0);

                if (maxPct > MAX_SINGLE_HOLDING_PCT) continue;

                TwrResponseDto twr = twrService.computeTwr(portfolio.getId(), range);
                if (twr.snapshotCount() < 2) continue;

                entries.add(new LeaderboardEntryDto(
                        0,
                        portfolio.getId(),
                        portfolio.getName(),
                        displayNames != null ? displayNames.getOrDefault(portfolio.getUserId(), null) : null,
                        twr.twrPercent(),
                        holdings.size(),
                        Math.round(maxPct * 10.0) / 10.0
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
                    e.username(), e.twrPercent(), e.holdingCount(), e.maxHoldingPct()));
        }
        return ranked;
    }
}
