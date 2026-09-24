package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.LeaderboardPortfolioClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardPortfolioClickRepository extends JpaRepository<LeaderboardPortfolioClick, Long> {

    @Query(value = """
            SELECT CAST(user_id AS BIGINT) AS user_id,
                   CAST(COUNT(*) AS BIGINT) AS total_checks,
                   CAST(COUNT(DISTINCT portfolio_id) AS BIGINT) AS distinct_portfolios,
                   CAST(COUNT(DISTINCT CAST(clicked_at AS DATE)) AS BIGINT) AS days_viewed
              FROM springhi.leaderboard_portfolio_clicks
             GROUP BY user_id
             ORDER BY days_viewed DESC, total_checks DESC
            """, nativeQuery = true)
    List<Object[]> findAllEngagementAggregates();

    @Query(value = "SELECT COUNT(DISTINCT CAST(clicked_at AS DATE)) FROM springhi.leaderboard_portfolio_clicks WHERE user_id = :userId", nativeQuery = true)
    long countDistinctClickDaysByUser(@Param("userId") Long userId);
}
