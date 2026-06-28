package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.PortfolioRecommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecommendationDto(
        Long id,
        String t,
        String n,
        String s,
        String action,
        double w,
        String r,
        String status,
        BigDecimal estimatedValue,
        Long transactionId,
        LocalDateTime generatedAt,
        String aiProvider
) {
    public static RecommendationDto from(PortfolioRecommendation rec) {
        return new RecommendationDto(
                rec.getId(),
                rec.getTicker(),
                rec.getName(),
                rec.getSector(),
                rec.getAction(),
                rec.getWeight() != null ? rec.getWeight().doubleValue() : 0.0,
                rec.getRationale(),
                rec.getStatus(),
                rec.getEstimatedValue(),
                rec.getTransactionId(),
                rec.getGeneratedAt(),
                rec.getAiProvider()
        );
    }
}
