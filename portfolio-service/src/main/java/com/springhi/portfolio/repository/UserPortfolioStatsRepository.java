package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.UserPortfolioStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPortfolioStatsRepository extends JpaRepository<UserPortfolioStats, Long> {
}
