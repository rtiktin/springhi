package com.springhi.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record QuoteResponse(
        String symbol,
        BigDecimal price,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        Long volume,
        BigDecimal previousClose,
        BigDecimal change,
        String changePercent,
        LocalDate tradingDay,
        LocalDateTime fetchedAt
) {}
