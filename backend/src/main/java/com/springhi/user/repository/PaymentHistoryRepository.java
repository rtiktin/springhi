package com.springhi.user.repository;

import com.springhi.user.model.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findByUserIdOrderByPaymentDateDesc(Long userId);
    List<PaymentHistory> findAllByOrderByPaymentDateDesc();
    List<PaymentHistory> findByPaymentDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT p.planName, MIN(p.paymentDate) FROM PaymentHistory p " +
            "WHERE p.status = 'COMPLETED' AND p.planName IN ('BASIC', 'PREMIUM') " +
            "GROUP BY p.userId, p.planName " +
            "HAVING MIN(p.paymentDate) >= :start AND MIN(p.paymentDate) < :end")
    List<Object[]> findFirstPaidSubscriptionDates(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    boolean existsByStripeInvoiceId(String stripeInvoiceId);
    Optional<PaymentHistory> findByStripeInvoiceId(String stripeInvoiceId);
}
