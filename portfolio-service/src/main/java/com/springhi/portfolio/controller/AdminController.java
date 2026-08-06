package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AdminPortfolioDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.UserServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final PortfolioRepository portfolioRepository;
    private final UserServiceClient userServiceClient;

    public AdminController(PortfolioRepository portfolioRepository,
                           UserServiceClient userServiceClient) {
        this.portfolioRepository = portfolioRepository;
        this.userServiceClient = userServiceClient;
    }

    @GetMapping("/stats/portfolios")
    public ResponseEntity<Map<String, Object>> getPortfolioStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int daysOffset) {
        if (principal == null || !principal.isAdmin()) {
            return ResponseEntity.status(403).build();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday  = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek   = now.toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime startOfMonth  = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfYear   = now.toLocalDate().withDayOfYear(1).atStartOfDay();

        long today     = portfolioRepository.findByCreatedAtBetween(startOfToday, now.plusDays(1)).size();
        long thisWeek  = portfolioRepository.findByCreatedAtBetween(startOfWeek, now.plusDays(1)).size();
        long thisMonth = portfolioRepository.findByCreatedAtBetween(startOfMonth, now.plusDays(1)).size();
        long thisYear  = portfolioRepository.findByCreatedAtBetween(startOfYear, now.plusDays(1)).size();

        LocalDateTime windowEnd   = startOfToday.minusDays(daysOffset);
        LocalDateTime windowStart = windowEnd.minusDays(29);
        List<Portfolio> recent = portfolioRepository.findByCreatedAtBetween(windowStart, windowEnd.plusDays(1));

        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) {
            dailyCounts.put(windowEnd.minusDays(i).toLocalDate().toString(), 0L);
        }
        for (Portfolio p : recent) {
            String day = p.getCreatedAt().toLocalDate().toString();
            dailyCounts.computeIfPresent(day, (k, v) -> v + 1);
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        dailyCounts.forEach((date, count) -> daily.add(Map.of("date", date, "count", count)));

        return ResponseEntity.ok(Map.of(
                "today", today,
                "thisWeek", thisWeek,
                "thisMonth", thisMonth,
                "thisYear", thisYear,
                "daily", daily
        ));
    }

    @GetMapping("/portfolios")
    public ResponseEntity<List<AdminPortfolioDto>> getAllPortfolios(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (principal == null || !principal.isAdmin()) {
            return ResponseEntity.status(403).build();
        }
        List<Portfolio> portfolios = portfolioRepository.findAll();
        List<Long> userIds = portfolios.stream().map(Portfolio::getUserId).distinct().collect(Collectors.toList());
        Map<Long, String> usernameMap = userServiceClient.getDisplayNames(userIds, authHeader);
        List<AdminPortfolioDto> dtos = portfolios.stream()
                .map(p -> AdminPortfolioDto.from(p, usernameMap.getOrDefault(p.getUserId(), "user_" + p.getUserId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
