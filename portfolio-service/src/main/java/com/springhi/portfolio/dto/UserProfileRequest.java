package com.springhi.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserProfileRequest(
        String riskLevel,
        String goal,
        Integer horizonYears,
        String liquidityNeeds,
        String knowledgeLevel,
        String additionalComments,
        BigDecimal availableCash,
        String currency,
        List<String> sectorConstraints
) {}
