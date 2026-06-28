package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionDto(
        Long id,
        String symbol,
        String type,
        BigDecimal quantity,
        BigDecimal price,
        LocalDateTime timestamp,
        Long recommendationId,
        LocalDateTime aiRunGeneratedAt,
        String aiProvider
) {
    public static TransactionDto of(Transaction t, Long recommendationId, LocalDateTime aiRunGeneratedAt, String aiProvider) {
        return new TransactionDto(
                t.getId(),
                t.getSymbol(),
                t.getType(),
                t.getQuantity(),
                t.getPrice(),
                t.getTimestamp(),
                recommendationId,
                aiRunGeneratedAt,
                aiProvider
        );
    }

    public static TransactionDto of(Transaction t) {
        return new TransactionDto(
                t.getId(),
                t.getSymbol(),
                t.getType(),
                t.getQuantity(),
                t.getPrice(),
                t.getTimestamp(),
                null,
                null,
                null
        );
    }
}
