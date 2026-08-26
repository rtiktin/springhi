package com.springhi.user.repository;

import com.springhi.user.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, Long> {
    Optional<Referral> findByReferredUserId(Long referredUserId);
    List<Referral> findByReferrerUserIdOrderBySignupAtDesc(Long referrerUserId);
    long countByReferrerUserIdAndStatus(Long referrerUserId, String status);
}
