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
        LocalDateTime aiRunGeneratedAt
) {
    public static TransactionDto of(Transaction t, Long recommendationId, LocalDateTime aiRunGeneratedAt) {
        return new TransactionDto(
                t.getId(),
                t.getSymbol(),
                t.getType(),
                t.getQuantity(),
                t.getPrice(),
                t.getTimestamp(),
                recommendationId,
                aiRunGeneratedAt
        );
    }

    public static TransactionDto of(Transaction t) {
        return of(t, null, null);
    }
}
