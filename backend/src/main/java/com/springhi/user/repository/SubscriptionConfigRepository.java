package com.springhi.user.repository;

import com.springhi.user.model.SubscriptionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionConfigRepository extends JpaRepository<SubscriptionConfig, Long> {
    Optional<SubscriptionConfig> findByPlanName(String planName);
    List<SubscriptionConfig> findAllByOrderByMonthlyPriceAsc();
}
