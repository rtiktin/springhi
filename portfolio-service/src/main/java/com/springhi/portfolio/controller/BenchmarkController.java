package com.springhi.portfolio.controller;

import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.SpyBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
