package com.springhi.portfolio.dto;

public record LeaderboardEntryDto(
        int rank,
        Long portfolioId,
        String portfolioName,
        String username,
        double twrPercent,
        int holdingCount,
        double maxHoldingPct
) {}
