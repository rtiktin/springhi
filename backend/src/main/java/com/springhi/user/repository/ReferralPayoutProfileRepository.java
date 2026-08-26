package com.springhi.user.repository;

import com.springhi.user.model.ReferralPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferralPayoutProfileRepository extends JpaRepository<ReferralPayoutProfile, Long> {
    Optional<ReferralPayoutProfile> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
