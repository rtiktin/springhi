package com.springhi.portfolio.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaderboardEntryDto(
        int rank,
        Long portfolioId,
        String portfolioName,
        String username,
        double twrPercent,
        Double marginVsSpy,
        int holdingCount,
        double maxHoldingPct,
        LocalDate competitionMonth,
        LocalDateTime createdAt,
        String goal
) {}
