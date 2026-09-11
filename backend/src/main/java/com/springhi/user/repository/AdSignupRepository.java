package com.springhi.user.repository;

import com.springhi.user.model.AdSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdSignupRepository extends JpaRepository<AdSignup, Long> {

    boolean existsByUserId(Long userId);

    // Signups attributed to one ad whose signup falls in [start, end].
    @Query("SELECT COUNT(s) FROM AdSignup s " +
            "WHERE s.adId = :adId AND s.signupAt BETWEEN :start AND :end")
    long countByAdIdAndSignupAtBetween(@Param("adId") Long adId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    // Of an ad's in-range attributed users, how many currently have an active paid subscription.
    @Query("SELECT COUNT(DISTINCT s.userId) FROM AdSignup s " +
            "WHERE s.adId = :adId AND s.signupAt BETWEEN :start AND :end " +
            "AND s.userId IN (SELECT u.userId FROM UserSubscription u " +
            "WHERE u.status = 'ACTIVE' AND u.planName <> 'FREE')")
    long countPaidForAd(@Param("adId") Long adId,
                        @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

    // Revenue actually collected from an ad's in-range attributed users, with payment date in range.
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentHistory p " +
            "WHERE p.status = 'COMPLETED' AND p.paymentDate BETWEEN :start AND :end " +
            "AND p.userId IN (SELECT s.userId FROM AdSignup s " +
            "WHERE s.adId = :adId AND s.signupAt BETWEEN :start AND :end)")
    BigDecimal sumRevenueForAd(@Param("adId") Long adId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    // Per-ad signups over the range. Each row: [adId(Long), count(Long)].
    @Query("SELECT s.adId, COUNT(s) FROM AdSignup s " +
            "WHERE s.signupAt BETWEEN :start AND :end GROUP BY s.adId")
    List<Object[]> countSignupsByAd(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    // Per-ad paid conversions over the range. Each row: [adId(Long), count(Long)].
    @Query("SELECT s.adId, COUNT(DISTINCT s.userId) FROM AdSignup s " +
            "WHERE s.signupAt BETWEEN :start AND :end " +
            "AND s.userId IN (SELECT u.userId FROM UserSubscription u " +
            "WHERE u.status = 'ACTIVE' AND u.planName <> 'FREE') GROUP BY s.adId")
    List<Object[]> countPaidByAd(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);

    // Per-ad collected revenue over the range. Each row: [adId(Long), amount(BigDecimal)].
    @Query("SELECT s.adId, COALESCE(SUM(p.amount), 0) FROM AdSignup s, PaymentHistory p " +
            "WHERE p.userId = s.userId AND p.status = 'COMPLETED' " +
            "AND p.paymentDate BETWEEN :start AND :end " +
            "AND s.signupAt BETWEEN :start AND :end GROUP BY s.adId")
    List<Object[]> sumRevenueByAd(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    @Modifying
    @Query("DELETE FROM AdSignup s WHERE s.adId = :adId")
    int deleteByAdId(@Param("adId") Long adId);
}
