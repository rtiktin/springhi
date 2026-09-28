package com.springhi.portfolio.controller;

import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.SpyBenchmarkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/benchmark")
public class BenchmarkController {

    private final SpyBenchmarkService spyBenchmarkService;

    public BenchmarkController(SpyBenchmarkService spyBenchmarkService) {
        this.spyBenchmarkService = spyBenchmarkService;
    }

    @GetMapping("/spy")
    public ResponseEntity<Map<String, Double>> getSpyReturns(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(spyBenchmarkService.getSpyReturns());
    }

    @GetMapping("/spy/period")
    public ResponseEntity<Map<String, Double>> getSpyReturnForPeriod(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (principal == null) return ResponseEntity.status(403).build();
        if (startDate.isAfter(endDate)) return ResponseEntity.badRequest().build();
        Double spyReturn = spyBenchmarkService.getSpyReturn(startDate, endDate);
        return ResponseEntity.ok(spyReturn == null ? Map.of() : Map.of("returnPercent", spyReturn));
    }
}
