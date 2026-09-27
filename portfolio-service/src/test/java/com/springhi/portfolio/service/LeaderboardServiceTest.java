package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.SubscribePromptConfigDto;
import com.springhi.portfolio.dto.UserSignupStatusDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.repository.AppSettingRepository;
import com.springhi.portfolio.repository.LeaderboardPortfolioClickRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
    void invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSubscribePromptConfig(new SubscribePromptConfigDto(-1, 3)));
        verifyNoInteractions(settings);
    }
}
