package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.LeaderboardEngagementDto;
import com.springhi.portfolio.dto.LeaderboardEntryDto;
import com.springhi.portfolio.dto.SubscribePromptConfigDto;
import com.springhi.portfolio.dto.SubscribePromptGateDto;
import com.springhi.portfolio.dto.UserSignupStatusDto;
import com.springhi.portfolio.model.AppSetting;
import com.springhi.portfolio.repository.AppSettingRepository;
import com.springhi.portfolio.dto.TwrResponseDto;
import com.springhi.portfolio.model.LeaderboardPortfolioClick;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.model.PortfolioProfile;
import com.springhi.portfolio.repository.LeaderboardPortfolioClickRepository;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import com.springhi.portfolio.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
    private final PortfolioProfileRepository profileRepository;
    private final LeaderboardPortfolioClickRepository clickRepository;
    private final AppSettingRepository settingRepository;
    private static final String JOIN_DAYS_KEY = "leaderboard.subscribe_prompt.join_days";
    private static final String VIEW_DAYS_KEY = "leaderboard.subscribe_prompt.view_days";

    public LeaderboardService(PortfolioRepository portfolioRepository,
                              PortfolioService portfolioService,
                              TwrService twrService,
                              UserServiceClient userServiceClient,
                              SpyBenchmarkService spyBenchmarkService,
                              PortfolioProfileRepository profileRepository,
                              LeaderboardPortfolioClickRepository clickRepository,
                              AppSettingRepository settingRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
        this.twrService = twrService;
        this.userServiceClient = userServiceClient;
        this.spyBenchmarkService = spyBenchmarkService;
        this.profileRepository = profileRepository;
        this.clickRepository = clickRepository;
        this.settingRepository = settingRepository;
    }

    public List<LeaderboardEntryDto> getLeaderboard(String range, String scope, Long userId, String jwtToken, String goal) {
        List<Portfolio> portfolios;
        if ("mine".equalsIgnoreCase(scope)) {
            portfolios = portfolioRepository.findByUserIdOrderByCreatedAtAsc(userId);
        } else {
            portfolios = portfolioRepository.findAll();
        }

        Map<Long, String> goalMap = getGoalMap();
        List<Portfolio> filtered = filterByGoal(portfolios, goal, goalMap);

        Map<Long, String> usernameMap = java.util.Collections.emptyMap();
        if (!"mine".equalsIgnoreCase(scope)) {
            List<Long> userIds = filtered.stream()
                    .map(Portfolio::getUserId).distinct().collect(Collectors.toList());
            usernameMap = userServiceClient.getDisplayNames(userIds, jwtToken);
        }

        Double spyReturn = getSpyReturnForRange(range);
        return buildEntries(filtered, range, usernameMap, !"mine".equalsIgnoreCase(scope), spyReturn, false, goalMap);
    }

    public List<LeaderboardEntryDto> getMonthlyLeaderboard(String monthStr, String jwtToken, String goal) {
        LocalDate competitionMonth = LocalDate.parse(monthStr + "-01");
        java.time.LocalDateTime to = competitionMonth.atStartOfDay();
        List<Portfolio> portfolios = portfolioRepository.findByCreatedAtLessThan(to);

        Map<Long, String> goalMap = getGoalMap();
        List<Portfolio> filtered = filterByGoal(portfolios, goal, goalMap);

        List<Long> userIds = filtered.stream()
                .map(Portfolio::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> usernameMap = userServiceClient.getDisplayNames(userIds, jwtToken);

        LocalDate anchor = competitionMonth;
        LocalDate today = LocalDate.now();
        long yearsElapsed = java.time.temporal.ChronoUnit.YEARS.between(competitionMonth, today);
        anchor = competitionMonth.plusYears(yearsElapsed);

        Double spyReturn = spyBenchmarkService.getSpyReturn(anchor);

        return buildEntries(filtered, null, usernameMap, true, spyReturn, false, goalMap, anchor, competitionMonth);
    }

    public SubscribePromptConfigDto getSubscribePromptConfig() {
        int joinDays = settingRepository.findById(JOIN_DAYS_KEY)
                .map(setting -> Integer.parseInt(setting.getValue())).orElse(30);
        int viewDays = settingRepository.findById(VIEW_DAYS_KEY)
                .map(setting -> Integer.parseInt(setting.getValue())).orElse(3);
        return new SubscribePromptConfigDto(joinDays, viewDays);
    }

    @Transactional
    public SubscribePromptConfigDto updateSubscribePromptConfig(SubscribePromptConfigDto config) {
        if (config == null || config.daysSinceJoined() < 0 || config.daysViewed() < 0) {
            throw new IllegalArgumentException("Thresholds must be non-negative whole numbers.");
        }
        settingRepository.save(new AppSetting(JOIN_DAYS_KEY, String.valueOf(config.daysSinceJoined())));
        settingRepository.save(new AppSetting(VIEW_DAYS_KEY, String.valueOf(config.daysViewed())));
        return config;
    }

    public SubscribePromptGateDto recordClick(Long userId, Long portfolioId, String jwtToken, boolean admin) {
        if (userId == null || portfolioId == null || portfolioRepository.findById(portfolioId)
                .filter(Portfolio::isEnabled).isEmpty()) {
            throw new IllegalArgumentException("Portfolio not found.");
        }
        Map<Long, UserSignupStatusDto> statuses = userServiceClient.getSignupStatuses(List.of(userId), jwtToken);
        UserSignupStatusDto user = statuses.get(userId);
        if (user == null || user.createdAt() == null) {
            throw new IllegalStateException("Unable to verify subscription status.");
        }
        LeaderboardPortfolioClick click = new LeaderboardPortfolioClick();
        click.setUserId(userId);
        click.setPortfolioId(portfolioId);
        click.setClickedAt(LocalDateTime.now());
        clickRepository.save(click);
        return new SubscribePromptGateDto(shouldPrompt(userId, user, admin));
    }

    public boolean shouldPrompt(Long userId, String jwtToken, boolean admin) {
        if (admin) return false;
        UserSignupStatusDto user = userServiceClient.getSignupStatuses(List.of(userId), jwtToken).get(userId);
        if (user == null || user.createdAt() == null) {
            throw new IllegalStateException("Unable to verify subscription status.");
        }
        return shouldPrompt(userId, user, false);
    }

    private boolean shouldPrompt(Long userId, UserSignupStatusDto user, boolean admin) {
        if (admin || user.subscribed()) return false;
        SubscribePromptConfigDto config = getSubscribePromptConfig();
        long daysSinceJoined = Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(user.createdAt().substring(0, 10)), LocalDate.now()));
        return daysSinceJoined >= config.daysSinceJoined()
                && clickRepository.countDistinctClickDaysByUser(userId) > config.daysViewed();
    }

    public List<LeaderboardEngagementDto> getEngagement(String jwtToken) {
        List<Object[]> rows = clickRepository.findAllEngagementAggregates();
        if (rows.isEmpty()) return List.of();
        List<Long> userIds = rows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .collect(Collectors.toList());
        Map<Long, String> usernameMap = userServiceClient.getDisplayNames(userIds, jwtToken);
        Map<Long, UserSignupStatusDto> statuses = userServiceClient.getSignupStatuses(userIds, jwtToken);
        List<LeaderboardEngagementDto> result = new ArrayList<>();
        for (Object[] r : rows) {
            Long userId = ((Number) r[0]).longValue();
            long totalChecks = ((Number) r[1]).longValue();
            long distinctPortfolios = ((Number) r[2]).longValue();
            long daysViewed = ((Number) r[3]).longValue();
            String username = usernameMap.getOrDefault(userId, "user-" + userId);
            UserSignupStatusDto user = statuses.get(userId);
            Long daysSinceJoined = user != null && user.createdAt() != null
                    ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(user.createdAt().substring(0, 10)), LocalDate.now())) : null;
            String subscriptionStatus = user != null ? user.planName() + " (" + user.status() + ")" : "Unknown";
            result.add(new LeaderboardEngagementDto(userId, username, daysViewed, totalChecks, distinctPortfolios,
                    daysSinceJoined, subscriptionStatus));
        }
        return result;
    }

    private Map<Long, String> getGoalMap() {
        return profileRepository.findAll().stream()
                .filter(pp -> pp.getGoal() != null)
                .collect(Collectors.toMap(PortfolioProfile::getPortfolioId, PortfolioProfile::getGoal, (a, b) -> a));
    }

    private List<Portfolio> filterByGoal(List<Portfolio> portfolios, String goal, Map<Long, String> goalMap) {
        String targetGoal = (goal == null || goal.isBlank() || "all".equalsIgnoreCase(goal) || "undefined".equalsIgnoreCase(goal) || "null".equalsIgnoreCase(goal))
                ? null : goal.trim();

        if (targetGoal == null) return portfolios;

        return portfolios.stream()
                .filter(p -> {
                    String pGoal = goalMap.get(p.getId());
                    return pGoal != null && pGoal.equalsIgnoreCase(targetGoal);
                })
                .collect(Collectors.toList());
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
                                                   Double spyReturn, boolean competitionOnly, Map<Long, String> goalMap) {
        return buildEntries(portfolios, range, usernameMap, includeUsername, spyReturn, competitionOnly, goalMap, null, null);
    }

    private List<LeaderboardEntryDto> buildEntries(List<Portfolio> portfolios, String range,
                                                   Map<Long, String> usernameMap, boolean includeUsername,
                                                   Double spyReturn, boolean competitionOnly, Map<Long, String> goalMap,
                                                   LocalDate anchor, LocalDate compMonth) {
        List<LeaderboardEntryDto> entries = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            if (!portfolio.isEnabled()) continue;
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

                TwrResponseDto twr = (anchor != null) 
                        ? twrService.computeTwr(portfolio.getId(), null, anchor)
                        : twrService.computeTwr(portfolio.getId(), range);
                        
                if (twr.snapshotCount() < 2) continue;

                String username = includeUsername
                        ? usernameMap.getOrDefault(portfolio.getUserId(), "user-" + portfolio.getUserId())
                        : null;

                Double margin = spyReturn != null ? twr.twrPercent() - spyReturn : null;
                LocalDate month = compMonth != null ? compMonth : portfolio.getCompetitionMonth();

                entries.add(new LeaderboardEntryDto(
                        0,
                        portfolio.getId(),
                        portfolio.getName(),
                        username,
                        twr.twrPercent(),
                        margin,
                        holdings.size(),
                        Math.round(maxPct * 10.0) / 10.0,
                        month,
                        portfolio.getCreatedAt(),
                        goalMap.get(portfolio.getId()),
                        twr.startDate(),
                        twr.endDate()
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
                    e.username(), e.twrPercent(), e.marginVsSpy(), e.holdingCount(), e.maxHoldingPct(), e.competitionMonth(), e.createdAt(), e.goal(),
                    e.twrStartDate(), e.twrEndDate()));
        }
        return ranked;
    }
}
