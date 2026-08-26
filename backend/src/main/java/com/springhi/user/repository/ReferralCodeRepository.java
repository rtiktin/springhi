package com.springhi.user.repository;

import com.springhi.user.model.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByCode(String code);
    Optional<ReferralCode> findByUserId(Long userId);
    List<ReferralCode> findByUserIdOrderByIdDesc(Long userId);
    boolean existsByCode(String code);
}
