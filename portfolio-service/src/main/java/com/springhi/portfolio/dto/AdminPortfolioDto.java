package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.Portfolio;
import java.time.LocalDateTime;

public record AdminPortfolioDto(
        Long id,
        Long userId,
        String username,
        String name,
        String description,
        LocalDateTime createdAt
) {
    public static AdminPortfolioDto from(Portfolio p, String username) {
        return new AdminPortfolioDto(
                p.getId(),
                p.getUserId(),
                username,
                p.getName(),
                p.getDescription(),
                p.getCreatedAt()
        );
    }
}
