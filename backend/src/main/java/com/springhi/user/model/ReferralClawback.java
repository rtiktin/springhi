package com.springhi.user.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only CLAWBACK ledger event. A refund or lost dispute on a referred user's invoice
 * reverses the referral fee that invoice generated. One clawback per accrual
 * ({@code referralCommissionId} is unique): clawbacks are full, not pro-rated, and idempotent so
 * repeated {@code charge.refunded} webhook deliveries or multiple partial refunds on the same
 * invoice do not double-clawback. The platform balance is the sum of accruals minus clawbacks
 * minus confirmed payouts; a clawback that exceeds the current locked balance drives the balance
 * negative and is recovered from future accruals (ledger-only, no Stripe transfer reversal).
 */
@Entity
@Table(name = "referral_clawbacks", schema = "springhi")
public class ReferralClawback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id", nullable = false)
    private Long referredUserId;

    @Column(name = "referral_commission_id", nullable = false, unique = true)
    private Long referralCommissionId;

    @Column(name = "payment_history_id")
    private Long paymentHistoryId;

    @Column(name = "stripe_invoice_id", length = 64)
    private String stripeInvoiceId;

    // Positive amount of the fee being reversed (the accrual's commissionAmount).
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 16)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ReferralClawback() {}

    public Long getId() { return id; }
    public Long getReferrerUserId() { return referrerUserId; }
    public void setReferrerUserId(Long referrerUserId) { this.referrerUserId = referrerUserId; }
    public Long getReferredUserId() { return referredUserId; }
    public void setReferredUserId(Long referredUserId) { this.referredUserId = referredUserId; }
    public Long getReferralCommissionId() { return referralCommissionId; }
    public void setReferralCommissionId(Long referralCommissionId) { this.referralCommissionId = referralCommissionId; }
    public Long getPaymentHistoryId() { return paymentHistoryId; }
    public void setPaymentHistoryId(Long paymentHistoryId) { this.paymentHistoryId = paymentHistoryId; }
    public String getStripeInvoiceId() { return stripeInvoiceId; }
    public void setStripeInvoiceId(String stripeInvoiceId) { this.stripeInvoiceId = stripeInvoiceId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
