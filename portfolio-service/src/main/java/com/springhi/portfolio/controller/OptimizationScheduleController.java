package com.springhi.portfolio.controller;

import com.springhi.portfolio.model.OptimizationSchedule;
import com.springhi.portfolio.repository.OptimizationScheduleRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.PortfolioService;
import com.springhi.portfolio.service.ScheduledOptimizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio/schedules")
public class OptimizationScheduleController {

    private final OptimizationScheduleRepository scheduleRepository;
    private final ScheduledOptimizationService scheduledOptimizationService;
    private final PortfolioService portfolioService;

    public OptimizationScheduleController(OptimizationScheduleRepository scheduleRepository,
                                          ScheduledOptimizationService scheduledOptimizationService,
                                          PortfolioService portfolioService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledOptimizationService = scheduledOptimizationService;
        this.portfolioService = portfolioService;
    }

    @GetMapping
    public ResponseEntity<List<OptimizationSchedule>> getSchedules(
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return ResponseEntity.ok(scheduleRepository.findByPortfolioId(portfolioId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<OptimizationSchedule>> getAllSchedulesForUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(scheduleRepository.findByUserId(principal.getId()));
    }

    @PostMapping
    public ResponseEntity<OptimizationSchedule> createSchedule(
            @RequestParam Long portfolioId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);

        String frequency  = (String) body.get("frequency");
        String provider   = body.getOrDefault("aiProvider", "gemini").toString();
        Integer dayOfWeek  = body.get("dayOfWeek")  != null ? Integer.parseInt(body.get("dayOfWeek").toString())  : null;
        Integer dayOfMonth = body.get("dayOfMonth") != null ? Integer.parseInt(body.get("dayOfMonth").toString()) : null;

        OptimizationSchedule created = scheduledOptimizationService.createSchedule(
                portfolioId, principal.getId(), frequency, provider, dayOfWeek, dayOfMonth);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OptimizationSchedule> updateSchedule(
            @PathVariable Long id,
            @RequestParam Long portfolioId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);

        return scheduleRepository.findById(id)
                .filter(s -> s.getPortfolioId().equals(portfolioId))
                .map(s -> {
                    String frequency  = body.getOrDefault("frequency",  s.getFrequency()).toString();
                    String provider   = body.getOrDefault("aiProvider", s.getAiProvider()).toString();
                    Integer dayOfWeek  = body.get("dayOfWeek")  != null ? Integer.parseInt(body.get("dayOfWeek").toString())  : s.getDayOfWeek();
                    Integer dayOfMonth = body.get("dayOfMonth") != null ? Integer.parseInt(body.get("dayOfMonth").toString()) : s.getDayOfMonth();
                    boolean enabled   = body.get("enabled") != null ? Boolean.parseBoolean(body.get("enabled").toString()) : s.isEnabled();
                    return ResponseEntity.ok(scheduledOptimizationService.updateSchedule(s, frequency, provider, dayOfWeek, dayOfMonth, enabled));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long id,
            @RequestParam Long portfolioId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        portfolioService.validatePortfolioOwnership(principal.getId(), portfolioId);
        return scheduleRepository.findById(id)
                .filter(s -> s.getPortfolioId().equals(portfolioId))
                .map(s -> {
                    scheduleRepository.delete(s);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
