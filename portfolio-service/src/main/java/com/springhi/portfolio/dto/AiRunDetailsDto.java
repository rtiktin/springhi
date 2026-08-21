package com.springhi.portfolio.dto;

import java.util.List;

public record AiRunDetailsDto(
        List<RecommendationDto> recommendations,
        PortfolioProfileDto profile,
        Long scheduleId,
        String scheduleFrequency,
        Integer confidenceScore
) {}
