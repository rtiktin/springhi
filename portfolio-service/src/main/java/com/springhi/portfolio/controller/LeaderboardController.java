package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.LeaderboardEntryDto;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard(
            @RequestParam(defaultValue = "1M") String range,
            @RequestParam(defaultValue = "all") String scope,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(leaderboardService.getLeaderboard(range, scope, principal.getId(), authHeader));
    }
}
