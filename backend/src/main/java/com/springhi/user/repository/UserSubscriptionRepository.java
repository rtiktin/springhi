package com.springhi.user.repository;

import com.springhi.user.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {
    Optional<UserSubscription> findByUserId(Long userId);

    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    Optional<UserSubscription> findByStripeCustomerId(String stripeCustomerId);

    List<UserSubscription> findByNextBillingDateBeforeAndPendingPlanNameNotNull(LocalDateTime when);

    List<UserSubscription> findByNextBillingDateBefore(LocalDateTime when);

    @Query("SELECT s.planName, COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' GROUP BY s.planName")
    List<Object[]> countActiveByPlan();

    @Query("SELECT s FROM UserSubscription s WHERE s.planName IN ('BASIC','PREMIUM') AND s.startDate BETWEEN :start AND :end")
    List<UserSubscription> findPaidSubscriptionsCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT s FROM UserSubscription s WHERE s.userId NOT IN (SELECT u.id FROM User u)")
    List<UserSubscription> findOrphanedSubscriptions();

    // Referral qualifier: count distinct referred users of this referrer who currently have an
    // active paid subscription (live = ACTIVE and not FREE). Fees only accrue once this is >= the
    // configured minimum (default 2) to block self-referral-only farming.
    @Query("SELECT COUNT(DISTINCT s.userId) FROM UserSubscription s " +
            "WHERE s.status = 'ACTIVE' AND s.planName <> 'FREE' " +
            "AND s.userId IN (SELECT r.referredUserId FROM Referral r " +
            "WHERE r.referrerUserId = :referrerId AND r.voided = false)")
    long countLiveReferred(@Param("referrerId") Long referrerId);
}
