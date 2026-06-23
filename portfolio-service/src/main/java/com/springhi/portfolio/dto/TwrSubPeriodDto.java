package com.springhi.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TwrSubPeriodDto(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal beginValue,
        BigDecimal endValue,
        BigDecimal netCashFlow,
        double periodReturnPercent
) {}
