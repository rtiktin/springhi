package com.springhi.portfolio.dto;

import java.time.LocalDate;
import java.util.List;

public record TwrResponseDto(
        double twrPercent,
        LocalDate startDate,
        LocalDate endDate,
        int snapshotCount,
        List<TwrSubPeriodDto> subPeriods
) {}
