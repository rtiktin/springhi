package com.springhi.user.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AdminRevenueDto(
    BigDecimal today,
    BigDecimal thisWeek,
    BigDecimal thisMonth,
    BigDecimal thisYear,
    BigDecimal allTime,
    List<Map<String, Object>> daily
) {}
