package com.springhi.user.repository;

import com.springhi.user.model.ReferralPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReferralPayoutRepository extends JpaRepository<ReferralPayout, Long> {
    List<ReferralPayout> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);
    List<ReferralPayout> findByRunIdOrderByReferrerUserIdAsc(String runId);

    // Idempotency for the monthly payout: one payout record per (referrer, month-run).
    boolean existsByRunIdAndReferrerUserId(String runId, Long referrerUserId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM ReferralPayout p " +
            "WHERE p.referrerUserId = :userId AND p.status IN :statuses")
    BigDecimal sumAmountByReferrerAndStatusIn(@Param("userId") Long userId,
                                              @Param("statuses") Collection<String> statuses);
}
