package com.springhi.portfolio.dto;

import java.util.List;

public record OptimizationResponse(
        List<RecommendationDto> recommendations,
        String error
) {}
