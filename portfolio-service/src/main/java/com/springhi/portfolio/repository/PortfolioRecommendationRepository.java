package com.springhi.portfolio.repository;

import com.springhi.portfolio.model.PortfolioRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PortfolioRecommendationRepository extends JpaRepository<PortfolioRecommendation, Long> {

    List<PortfolioRecommendation> findByUserIdAndGeneratedAtBetweenOrderByActionDescIdAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Modifying
    @Transactional
    @Query("DELETE FROM PortfolioRecommendation r WHERE r.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
