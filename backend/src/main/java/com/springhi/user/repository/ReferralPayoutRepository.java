package com.springhi.user.repository;

import com.springhi.user.model.ReferralPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralPayoutRepository extends JpaRepository<ReferralPayout, Long> {
    List<ReferralPayout> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);
    List<ReferralPayout> findByRunIdOrderByReferrerUserIdAsc(String runId);
}
