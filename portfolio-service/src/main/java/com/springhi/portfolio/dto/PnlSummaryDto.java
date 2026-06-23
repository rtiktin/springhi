package com.springhi.portfolio.dto;

import java.math.BigDecimal;

public record PnlSummaryDto(
        BigDecimal currentMarketValue,
        BigDecimal currentCostBasis,
        BigDecimal unrealizedPnl,
        BigDecimal totalBuyCost,
        BigDecimal totalSellProceeds,
        BigDecimal realizedPnl,
        BigDecimal totalPnl
) {}
