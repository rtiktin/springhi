package com.springhi.user.repository;

import com.springhi.user.model.ReferralCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralCommissionRepository extends JpaRepository<ReferralCommission, Long> {
    Optional<ReferralCommission> findByPaymentHistoryId(Long paymentHistoryId);
    List<ReferralCommission> findByReferrerUserIdAndStatusOrderByCreatedAtAsc(Long referrerUserId, String status);
    List<ReferralCommission> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM ReferralCommission c WHERE c.referrerUserId = :userId AND c.status = :status")
    BigDecimal sumAmountByReferrerAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query("SELECT DISTINCT c.referrerUserId FROM ReferralCommission c WHERE c.status = :status")
    List<Long> findDistinctReferrerUserIdsByStatus(@Param("status") String status);

    @Modifying
    @Query("UPDATE ReferralCommission c SET c.status = :newStatus, c.payoutId = :payoutId " +
            "WHERE c.referrerUserId = :referrerUserId AND c.status = :accruedStatus")
    int markPaid(@Param("referrerUserId") Long referrerUserId,
                 @Param("payoutId") Long payoutId,
                 @Param("accruedStatus") String accruedStatus,
                 @Param("newStatus") String newStatus);
}
