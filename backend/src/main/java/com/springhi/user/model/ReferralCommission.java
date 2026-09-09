package com.springhi.user.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_commissions", schema = "springhi")
public class ReferralCommission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referral_id", nullable = false)
    private Long referralId;

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id", nullable = false)
    private Long referredUserId;

    // Not unique: an annual subscription's upfront invoice is amortized into one accrual row per
    // month (12 installments), all sharing this payment_history_id. Monthly invoices get 1 row.
    @Column(name = "payment_history_id", nullable = false)
    private Long paymentHistoryId;

    @Column(name = "basis_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal basisAmount;

    @Column(name = "commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    // Accrual lifecycle: PENDING (under 31-day hold) -> LOCKED (hold passed, payable) -> VOID
    // (refunded while pending, never payable). Legacy ACCRUED/PAID statuses are tolerated as LOCKED
    // for balance math. Set when a PENDING accrual matures past the hold period.
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // When this PENDING accrual becomes eligible to mature to LOCKED. For a monthly invoice this is
    // createdAt + holdDays; for an amortized annual invoice, installment i matures at
    // firstPaidInvoiceAt + i months + holdDays, so the fee is recognized over the subscription term.
    // Null on legacy rows: the lock job falls back to createdAt + holdDays.
    @Column(name = "lock_eligible_at")
    private LocalDateTime lockEligibleAt;

    @Column(name = "payout_id")
    private Long payoutId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ReferralCommission() {}

    public Long getId() { return id; }
    public Long getReferralId() { return referralId; }
    public void setReferralId(Long referralId) { this.referralId = referralId; }
    public Long getReferrerUserId() { return referrerUserId; }
    public void setReferrerUserId(Long referrerUserId) { this.referrerUserId = referrerUserId; }
    public Long getReferredUserId() { return referredUserId; }
    public void setReferredUserId(Long referredUserId) { this.referredUserId = referredUserId; }
    public Long getPaymentHistoryId() { return paymentHistoryId; }
    public void setPaymentHistoryId(Long paymentHistoryId) { this.paymentHistoryId = paymentHistoryId; }
    public BigDecimal getBasisAmount() { return basisAmount; }
    public void setBasisAmount(BigDecimal basisAmount) { this.basisAmount = basisAmount; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) { this.commissionAmount = commissionAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getLockEligibleAt() { return lockEligibleAt; }
    public void setLockEligibleAt(LocalDateTime lockEligibleAt) { this.lockEligibleAt = lockEligibleAt; }
    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
