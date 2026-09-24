package com.springhi.portfolio.dto;

public record LeaderboardEngagementDto(
        Long userId,
        String username,
        long daysViewed,
        long portfolioChecks,
        long distinctPortfoliosViewed,
        Long daysSinceJoined,
        String subscriptionStatus
) {}
