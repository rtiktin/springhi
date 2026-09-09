package com.springhi.user.repository;

import com.springhi.user.model.ReferralCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReferralCommissionRepository extends JpaRepository<ReferralCommission, Long> {
    // Returns all accrual rows for a payment (1 for monthly, up to 12 for an amortized annual
    // invoice). Used for accrual idempotency and clawback matching.
    List<ReferralCommission> findByPaymentHistoryId(Long paymentHistoryId);
    List<ReferralCommission> findByReferrerUserIdAndStatusOrderByCreatedAtAsc(Long referrerUserId, String status);
    List<ReferralCommission> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM ReferralCommission c WHERE c.referrerUserId = :userId AND c.status = :status")
    BigDecimal sumAmountByReferrerAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM ReferralCommission c " +
            "WHERE c.referrerUserId = :userId AND c.status IN :statuses")
    BigDecimal sumAmountByReferrerAndStatusIn(@Param("userId") Long userId,
                                              @Param("statuses") Collection<String> statuses);

    @Query("SELECT DISTINCT c.referrerUserId FROM ReferralCommission c WHERE c.status = :status")
    List<Long> findDistinctReferrerUserIdsByStatus(@Param("status") String status);

    // Referrers who currently have any payable (realized) accrual — candidates for the monthly payout.
    @Query("SELECT DISTINCT c.referrerUserId FROM ReferralCommission c " +
            "WHERE c.status IN ('LOCKED','ACCRUED','PAID')")
    List<Long> findDistinctReferrersWithRealizedAccruals();

    // PENDING accruals whose hold has elapsed: mature when lockEligibleAt <= now (amortized/new
    // rows), or — for legacy rows with no lockEligibleAt — when createdAt <= cutoff (now - holdDays).
    @Query("SELECT c FROM ReferralCommission c WHERE c.status = 'PENDING' AND (" +
            "(c.lockEligibleAt IS NOT NULL AND c.lockEligibleAt <= :now) OR " +
            "(c.lockEligibleAt IS NULL AND c.createdAt <= :cutoff))")
    List<ReferralCommission> findPendingMatured(@Param("now") LocalDateTime now,
                                                @Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("UPDATE ReferralCommission c SET c.status = :newStatus, c.payoutId = :payoutId " +
            "WHERE c.referrerUserId = :referrerUserId AND c.status = :accruedStatus")
    int markPaid(@Param("referrerUserId") Long referrerUserId,
                 @Param("payoutId") Long payoutId,
                 @Param("accruedStatus") String accruedStatus,
                 @Param("newStatus") String newStatus);

    // Audit-link LOCKED accruals to the payout that covered them, without changing status. The
    // ledger balance subtracts confirmed payouts, so accruals stay LOCKED and this row offsets them;
    // payoutId is for reconciliation only. Only touches accruals not already linked to a payout.
    @Modifying
    @Query("UPDATE ReferralCommission c SET c.payoutId = :payoutId " +
            "WHERE c.referrerUserId = :referrerUserId AND c.status = 'LOCKED' AND c.payoutId IS NULL")
    int linkPayout(@Param("referrerUserId") Long referrerUserId, @Param("payoutId") Long payoutId);
}
