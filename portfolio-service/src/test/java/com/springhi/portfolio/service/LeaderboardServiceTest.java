package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.SubscribePromptConfigDto;
import com.springhi.portfolio.dto.UserSignupStatusDto;
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
    private final LeaderboardService service = new LeaderboardService(null, null, null, users, null, null, clicks, settings);

    @Test
    void defaultThresholdRequiresAtLeastThirtyJoinDaysAndMoreThanThreeViewDays() {
        UserSignupStatusDto oldFree = new UserSignupStatusDto(7L, LocalDate.now().minusDays(30).atStartOfDay().toString(), "FREE", "ACTIVE", false);
        when(users.getSignupStatuses(List.of(7L), "token")).thenReturn(Map.of(7L, oldFree));
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

        assertFalse(service.shouldPrompt(7L, "token", false));
        assertFalse(service.shouldPrompt(7L, "token", false));
        assertFalse(service.shouldPrompt(7L, "token", true));
    }

    @Test
    void thresholdsAreReadAfreshForEachCheck() {
        when(users.getSignupStatuses(List.of(7L), "token"))
                .thenReturn(Map.of(7L, new UserSignupStatusDto(7L, LocalDate.now().minusDays(15).atStartOfDay().toString(), "FREE", "ACTIVE", false)));
        when(clicks.countDistinctClickDaysByUser(7L)).thenReturn(4L);
        assertFalse(service.shouldPrompt(7L, "token", false));
        when(settings.findById("leaderboard.subscribe_prompt.join_days"))
                .thenReturn(java.util.Optional.of(new com.springhi.portfolio.model.AppSetting("leaderboard.subscribe_prompt.join_days", "10")));
        assertTrue(service.shouldPrompt(7L, "token", false));
    }

    @Test
    void invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSubscribePromptConfig(new SubscribePromptConfigDto(-1, 3)));
        verifyNoInteractions(settings);
    }
}
