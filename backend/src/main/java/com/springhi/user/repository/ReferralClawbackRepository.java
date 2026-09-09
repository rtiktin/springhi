package com.springhi.user.repository;

import com.springhi.user.model.ReferralClawback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ReferralClawbackRepository extends JpaRepository<ReferralClawback, Long> {
    boolean existsByReferralCommissionId(Long referralCommissionId);

    List<ReferralClawback> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM ReferralClawback c WHERE c.referrerUserId = :userId")
    BigDecimal sumAmountByReferrer(@Param("userId") Long userId);
}
