package com.springhi.user.repository;

import com.springhi.user.model.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {
    List<PaymentHistory> findByUserIdOrderByPaymentDateDesc(Long userId);
    List<PaymentHistory> findAllByOrderByPaymentDateDesc();
    List<PaymentHistory> findByPaymentDateBetween(LocalDateTime start, LocalDateTime end);
    boolean existsByStripeInvoiceId(String stripeInvoiceId);
}
