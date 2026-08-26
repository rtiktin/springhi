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

    @Column(name = "payment_history_id", nullable = false, unique = true)
    private Long paymentHistoryId;

    @Column(name = "basis_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal basisAmount;

    @Column(name = "commission_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

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
    public Long getPayoutId() { return payoutId; }
    public void setPayoutId(Long payoutId) { this.payoutId = payoutId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
