package com.springhi.user.repository;

import com.springhi.user.model.AdStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdStatRepository extends JpaRepository<AdStat, Long> {

    Optional<AdStat> findByAdIdAndStatDate(Long adId, LocalDate statDate);

    List<AdStat> findByAdIdOrderByStatDateAsc(Long adId);

    // Aggregated metrics for one ad over a date range (inclusive). Returns a single-element list
    // whose Object[] row is [impressions, clicks, spend, conversions, revenue]; empty if no stats.
    @Query("SELECT COALESCE(SUM(s.impressions), 0), COALESCE(SUM(s.clicks), 0), " +
            "COALESCE(SUM(s.spend), 0), COALESCE(SUM(s.conversions), 0), COALESCE(SUM(s.revenue), 0) " +
            "FROM AdStat s WHERE s.adId = :adId AND s.statDate BETWEEN :from AND :to")
    List<Object[]> sumByAdIdAndDateRange(@Param("adId") Long adId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    // Aggregated metrics per ad over a date range. Each Object[] row is:
    // [adId(Long), impressions(Long), clicks(Long), spend(BigDecimal), conversions(Long), revenue(BigDecimal)].
    @Query("SELECT s.adId, COALESCE(SUM(s.impressions), 0), COALESCE(SUM(s.clicks), 0), " +
            "COALESCE(SUM(s.spend), 0), COALESCE(SUM(s.conversions), 0), COALESCE(SUM(s.revenue), 0) " +
            "FROM AdStat s WHERE s.statDate BETWEEN :from AND :to GROUP BY s.adId")
    List<Object[]> sumByAdAndDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying
    @Query("DELETE FROM AdStat s WHERE s.adId = :adId")
    int deleteByAdId(@Param("adId") Long adId);
}
