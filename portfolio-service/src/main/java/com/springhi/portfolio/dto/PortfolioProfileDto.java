package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.PortfolioProfile;
import java.util.Arrays;
import java.util.List;

public record PortfolioProfileDto(
        Long portfolioId,
        String riskLevel,
        String goal,
        Integer horizonYears,
        String liquidityNeeds,
        String additionalComments,
        String currency,
        List<String> sectorConstraints
) {
    public static PortfolioProfileDto from(PortfolioProfile p) {
        List<String> sectors = (p.getSectorConstraints() != null && !p.getSectorConstraints().isBlank())
                ? Arrays.stream(p.getSectorConstraints().split(","))
                        .map(String::trim).filter(s -> !s.isBlank()).toList()
                : List.of();
        return new PortfolioProfileDto(
                p.getPortfolioId(),
                p.getRiskLevel(),
                p.getGoal(),
                p.getHorizonYears(),
                p.getLiquidityNeeds(),
                p.getAdditionalComments(),
                p.getCurrency() != null ? p.getCurrency() : "USD",
                sectors
        );
    }
}
