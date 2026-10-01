package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.SubscribePromptConfigDto;
import com.springhi.portfolio.dto.TwrResponseDto;
import com.springhi.portfolio.dto.UserSignupStatusDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.repository.AppSettingRepository;
import com.springhi.portfolio.repository.LeaderboardPortfolioClickRepository;
import com.springhi.portfolio.repository.PortfolioProfileRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaderboardServiceTest {
    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private final LeaderboardPortfolioClickRepository clicks = mock(LeaderboardPortfolioClickRepository.class);
    private final UserServiceClient users = mock(UserServiceClient.class);
    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final LeaderboardService service = new LeaderboardService(portfolios, null, null, users, null, null, clicks, settings);

    @Test
    void defaultThresholdRequiresAtLeastThirtyJoinDaysAndMoreThanThreeViewDays() {
        UserSignupStatusDto oldFree = new UserSignupStatusDto(7L, LocalDate.now().minusDays(30).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, oldFree));
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of());
        when(clicks.countDistinctClickDaysByUser(7L)).thenReturn(3L, 4L);

        assertEquals(new SubscribePromptConfigDto(30, 3), service.getSubscribePromptConfig());
        assertFalse(service.shouldPrompt(7L, "token", false));
        assertTrue(service.shouldPrompt(7L, "token", false));
    }

    @Test
    void paidAndNewUsersAreNotPrompted() {
        when(users.getSignupStatuses(List.of(7L), "token"))
                .thenReturn(Map.of(7L, new UserSignupStatusDto(7L, LocalDate.now().minusDays(31).atStartOfDay().toString(), "BASIC", "ACTIVE", true)))
                .thenReturn(Map.of(7L, new UserSignupStatusDto(7L, LocalDate.now().minusDays(29).atStartOfDay().toString(), "FREE", "ACTIVE", false)));
        when(clicks.countDistinctClickDaysByUser(7L)).thenReturn(8L);
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of());

        assertFalse(service.shouldPrompt(7L, "token", false));
        assertFalse(service.shouldPrompt(7L, "token", false));
        assertFalse(service.shouldPrompt(7L, "token", true));
    }

    @Test
    void thresholdsAreReadAfreshForEachCheck() {
        when(users.getSignupStatuses(List.of(7L), "token"))
                .thenReturn(Map.of(7L, new UserSignupStatusDto(7L, LocalDate.now().minusDays(15).atStartOfDay().toString(), "FREE", "ACTIVE", false)));
        when(clicks.countDistinctClickDaysByUser(7L)).thenReturn(4L);
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of());
        assertFalse(service.shouldPrompt(7L, "token", false));
        when(settings.findById("leaderboard.subscribe_prompt.join_days"))
                .thenReturn(java.util.Optional.of(new com.springhi.portfolio.model.AppSetting("leaderboard.subscribe_prompt.join_days", "10")));
        assertTrue(service.shouldPrompt(7L, "token", false));
    }

    @Test
    void linkedFreeAccountOverBothThresholdsBlocksNewAccountWithDistinctReason() {
        UserSignupStatusDto newcomer = new UserSignupStatusDto(7L, LocalDate.now().minusDays(2).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        UserSignupStatusDto linked = new UserSignupStatusDto(8L, LocalDate.now().minusDays(31).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, newcomer));
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of(8L, linked));
        when(clicks.countDistinctClickDaysByUser(8L)).thenReturn(4L);
        Portfolio portfolio = new Portfolio();
        portfolio.setEnabled(true);
        when(portfolios.findById(9L)).thenReturn(java.util.Optional.of(portfolio));

        assertTrue(service.shouldPrompt(7L, "token", false));
        var gate = service.recordClick(7L, 9L, "token", false);
        assertTrue(gate.forceSubscribe());
        assertEquals("LINKED_FREE_USER_LIMIT", gate.reason());
        assertFalse(service.shouldPrompt(7L, "token", true));
    }

    @Test
    void freeUserWithoutVerifiedPhoneMustVerifyOnSecondDistinctClickDay() {
        UserSignupStatusDto user = new UserSignupStatusDto(7L, LocalDate.now().atStartOfDay().toString(), "FREE", "ACTIVE", false, false);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, user));
        Portfolio portfolio = new Portfolio();
        portfolio.setEnabled(true);
        when(portfolios.findById(9L)).thenReturn(java.util.Optional.of(portfolio));
        when(portfolios.findById(10L)).thenReturn(java.util.Optional.of(portfolio));
        when(clicks.countDistinctClickDaysByUser(7L)).thenReturn(1L, 1L, 2L, 2L);

        assertFalse(service.recordClick(7L, 9L, "token", false).forceSubscribe());
        assertFalse(service.recordClick(7L, 10L, "token", false).forceSubscribe());
        var gate = service.recordClick(7L, 9L, "token", false);
        assertTrue(gate.forceSubscribe());
        assertEquals("PHONE_VERIFICATION_REQUIRED", gate.reason());
        assertTrue(service.shouldPrompt(7L, "token", false));
        verify(users, never()).getLinkedPhoneSignupStatuses(anyString());

        UserSignupStatusDto verified = new UserSignupStatusDto(7L, user.createdAt(), "FREE", "ACTIVE", false, true);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, verified));
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of());
        assertFalse(service.shouldPrompt(7L, "token", false));
    }

    @Test
    void linkedAccountsBelowThresholdOrSubscribedDoNotBlock() {
        UserSignupStatusDto newcomer = new UserSignupStatusDto(7L, LocalDate.now().minusDays(2).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        UserSignupStatusDto linked = new UserSignupStatusDto(8L, LocalDate.now().minusDays(31).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        UserSignupStatusDto paid = new UserSignupStatusDto(9L, LocalDate.now().minusDays(31).atStartOfDay().toString(), "BASIC", "ACTIVE", true);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, newcomer));
        when(users.getLinkedPhoneSignupStatuses("token")).thenReturn(Map.of(8L, linked, 9L, paid));
        when(clicks.countDistinctClickDaysByUser(8L)).thenReturn(3L);

        assertFalse(service.shouldPrompt(7L, "token", false));
        verify(clicks, never()).countDistinctClickDaysByUser(9L);
    }

    @Test
    void leaderboardMarginUsesTheSameDatesAsTheDisplayedTwr() {
        PortfolioService portfolioService = mock(PortfolioService.class);
        TwrService twrService = mock(TwrService.class);
        SpyBenchmarkService benchmark = mock(SpyBenchmarkService.class);
        PortfolioProfileRepository profiles = mock(PortfolioProfileRepository.class);
        LeaderboardService leaderboard = new LeaderboardService(portfolios, portfolioService, twrService, users,
                benchmark, profiles, clicks, settings);
        Portfolio portfolio = new Portfolio();
        portfolio.setId(34L);
        portfolio.setUserId(7L);
        portfolio.setName("Test portfolio");
        LocalDate firstSnapshot = LocalDate.of(2026, 9, 10);
        LocalDate lastSnapshot = LocalDate.of(2026, 9, 17);
        when(portfolios.findByUserIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(portfolio));
        when(profiles.findAll()).thenReturn(List.of());
        List<AssetWithPrice> holdings = java.util.stream.IntStream.range(0, 5).mapToObj(i -> {
            AssetWithPrice holding = new AssetWithPrice();
            holding.setMarketValue(new BigDecimal("100"));
            return holding;
        }).toList();
        when(portfolioService.getUserAssetsWithPrices(34L)).thenReturn(holdings);
        when(twrService.computeTwr(34L, "1M"))
                .thenReturn(new TwrResponseDto(15.0, firstSnapshot, lastSnapshot, 2, List.of()));
        when(benchmark.getSpyReturn(firstSnapshot, lastSnapshot)).thenReturn(5.0);

        var entries = leaderboard.getLeaderboard("1M", "mine", 7L, "token", null);

        assertEquals(1, entries.size());
        assertEquals(10.0, entries.get(0).marginVsSpy());
        assertEquals(firstSnapshot, entries.get(0).twrStartDate());
        assertEquals(lastSnapshot, entries.get(0).twrEndDate());
        verify(benchmark).getSpyReturn(firstSnapshot, lastSnapshot);
        verify(benchmark, never()).getSpyReturns();
    }

    @Test
    void monthlyLeaderboardsUseOnlyPortfoliosCreatedInThePreviousMonth() {
        PortfolioService portfolioService = mock(PortfolioService.class);
        TwrService twrService = mock(TwrService.class);
        SpyBenchmarkService benchmark = mock(SpyBenchmarkService.class);
        PortfolioProfileRepository profiles = mock(PortfolioProfileRepository.class);
        LeaderboardService leaderboard = new LeaderboardService(portfolios, portfolioService, twrService, users,
                benchmark, profiles, clicks, settings);
        Portfolio julyPortfolio = new Portfolio();
        julyPortfolio.setId(1L);
        julyPortfolio.setUserId(7L);
        julyPortfolio.setName("July portfolio");
        Portfolio augustPortfolio = new Portfolio();
        augustPortfolio.setId(2L);
        augustPortfolio.setUserId(8L);
        augustPortfolio.setName("August portfolio");
        when(portfolios.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0)))
                .thenReturn(List.of(julyPortfolio));
        when(portfolios.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0)))
                .thenReturn(List.of(augustPortfolio));
        when(profiles.findAll()).thenReturn(List.of());
        when(users.getDisplayNames(anyList(), eq("token"))).thenReturn(Map.of(7L, "july", 8L, "august"));
        List<AssetWithPrice> holdings = java.util.stream.IntStream.range(0, 5).mapToObj(i -> {
            AssetWithPrice holding = new AssetWithPrice();
            holding.setMarketValue(new BigDecimal("100"));
            return holding;
        }).toList();
        when(portfolioService.getUserAssetsWithPrices(anyLong())).thenReturn(holdings);
        when(twrService.computeTwr(1L, null, LocalDate.of(2026, 8, 1)))
                .thenReturn(new TwrResponseDto(10.0, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 2, List.of()));
        when(twrService.computeTwr(2L, null, LocalDate.of(2026, 9, 1)))
                .thenReturn(new TwrResponseDto(12.0, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 2, List.of()));

        var august = leaderboard.getMonthlyLeaderboard("2026-08", "token", null);
        var september = leaderboard.getMonthlyLeaderboard("2026-09", "token", null);

        assertEquals(List.of(1L), august.stream().map(e -> e.portfolioId()).toList());
        assertEquals(List.of(2L), september.stream().map(e -> e.portfolioId()).toList());
        verify(portfolios, never()).findByCreatedAtLessThan(any());
    }

    @Test
    void invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSubscribePromptConfig(new SubscribePromptConfigDto(-1, 3)));
        verifyNoInteractions(settings);
    }
}
