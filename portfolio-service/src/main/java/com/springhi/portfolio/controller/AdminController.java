package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.AdminPortfolioDto;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.UserServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
