package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.PortfolioRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioRecommendationRepository extends JpaRepository<PortfolioRecommendation, Long> {

    List<PortfolioRecommendation> findByUserIdAndGeneratedAtBetweenOrderByActionDescIdAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    List<PortfolioRecommendation> findByPortfolioIdAndGeneratedAtBetweenOrderByActionDescIdAsc(
            Long portfolioId, LocalDateTime start, LocalDateTime end);

    List<PortfolioRecommendation> findByTransactionIdIn(Collection<Long> transactionIds);

    List<PortfolioRecommendation> findByPortfolioIdAndGeneratedAtOrderByActionDescIdAsc(
            Long portfolioId, LocalDateTime generatedAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM PortfolioRecommendation r WHERE r.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PortfolioRecommendation r WHERE r.portfolioId = :portfolioId")
    void deleteAllForPortfolio(@Param("portfolioId") Long portfolioId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PortfolioRecommendation r WHERE r.portfolioId = :portfolioId AND r.status = 'PENDING'")
    void deletePendingForPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("SELECT COUNT(DISTINCT r.generatedAt) FROM PortfolioRecommendation r WHERE r.userId = :userId AND r.generatedAt >= :since")
    long countOptimizationRunsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT r.generatedAt FROM PortfolioRecommendation r WHERE r.portfolioId = :portfolioId GROUP BY r.generatedAt ORDER BY r.generatedAt DESC")
    List<LocalDateTime> findDistinctGeneratedAtByPortfolioIdOrderByDesc(@Param("portfolioId") Long portfolioId);
}
