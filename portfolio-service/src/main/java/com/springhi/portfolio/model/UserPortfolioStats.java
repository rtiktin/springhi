package com.springhi.portfolio.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_portfolio_stats", schema = "springhi")
public class UserPortfolioStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_created", nullable = false)
    private int totalCreated = 0;

    public UserPortfolioStats() {}

    public UserPortfolioStats(Long userId) {
        this.userId = userId;
        this.totalCreated = 0;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public int getTotalCreated() { return totalCreated; }
    public void setTotalCreated(int totalCreated) { this.totalCreated = totalCreated; }
}
