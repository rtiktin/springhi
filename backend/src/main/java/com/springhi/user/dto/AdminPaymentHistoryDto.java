package com.springhi.user.dto;

import com.springhi.user.model.PaymentHistory;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentHistoryDto(
    Long id,
    Long userId,
    String username,
    String email,
    String planName,
    BigDecimal amount,
    String billingCycle,
    String status,
    LocalDateTime paymentDate
) {
    public static AdminPaymentHistoryDto from(PaymentHistory p, String username, String email) {
        return new AdminPaymentHistoryDto(
            p.getId(),
            p.getUserId(),
            username,
            email,
            p.getPlanName(),
            p.getAmount(),
            p.getBillingCycle(),
            p.getStatus(),
            p.getPaymentDate()
        );
    }
}
