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

    @Query("SELECT s.planName, COUNT(s) FROM UserSubscription s WHERE s.status = 'ACTIVE' GROUP BY s.planName")
    List<Object[]> countActiveByPlan();

    @Query("SELECT s FROM UserSubscription s WHERE s.planName IN ('BASIC','PREMIUM') AND s.startDate BETWEEN :start AND :end")
    List<UserSubscription> findPaidSubscriptionsCreatedBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
