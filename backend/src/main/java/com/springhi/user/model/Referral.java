package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "referrals", schema = "springhi")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referral_code_id", nullable = false)
    private Long referralCodeId;

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id", nullable = false, unique = true)
    private Long referredUserId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "click_ip_hash", length = 128)
    private String clickIpHash;

    @Column(name = "click_user_agent", length = 512)
    private String clickUserAgent;

    @Column(name = "click_at")
    private LocalDateTime clickAt;

    @Column(name = "signup_at")
    private LocalDateTime signupAt;

    @Column(name = "first_year_end")
    private LocalDate firstYearEnd;

    // Anchors both the 31-day hold and the 12-month earning window to the referred user's FIRST
    // paid invoice (set on first accrueCommissionOnPayment), not signup. Null until that invoice lands.
    @Column(name = "first_paid_invoice_at")
    private LocalDateTime firstPaidInvoiceAt;

    // Self-referral fraud: the referred user's card fingerprint is re-checked at first accrual (it
    // is not known until they add a payment method). A match against the referrer voids the referral.
    @Column(name = "card_fingerprint_checked", nullable = false)
    private boolean cardFingerprintChecked = false;

    @Column(name = "voided", nullable = false)
    private boolean voided = false;

    @Column(name = "converted_at")
    private LocalDateTime convertedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Referral() {}

    public Long getId() { return id; }
    public Long getReferralCodeId() { return referralCodeId; }
    public void setReferralCodeId(Long referralCodeId) { this.referralCodeId = referralCodeId; }
    public Long getReferrerUserId() { return referrerUserId; }
    public void setReferrerUserId(Long referrerUserId) { this.referrerUserId = referrerUserId; }
    public Long getReferredUserId() { return referredUserId; }
    public void setReferredUserId(Long referredUserId) { this.referredUserId = referredUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClickIpHash() { return clickIpHash; }
    public void setClickIpHash(String clickIpHash) { this.clickIpHash = clickIpHash; }
    public String getClickUserAgent() { return clickUserAgent; }
    public void setClickUserAgent(String clickUserAgent) { this.clickUserAgent = clickUserAgent; }
    public LocalDateTime getClickAt() { return clickAt; }
    public void setClickAt(LocalDateTime clickAt) { this.clickAt = clickAt; }
    public LocalDateTime getSignupAt() { return signupAt; }
    public void setSignupAt(LocalDateTime signupAt) { this.signupAt = signupAt; }
    public LocalDate getFirstYearEnd() { return firstYearEnd; }
    public void setFirstYearEnd(LocalDate firstYearEnd) { this.firstYearEnd = firstYearEnd; }
    public LocalDateTime getFirstPaidInvoiceAt() { return firstPaidInvoiceAt; }
    public void setFirstPaidInvoiceAt(LocalDateTime firstPaidInvoiceAt) { this.firstPaidInvoiceAt = firstPaidInvoiceAt; }
    public boolean isCardFingerprintChecked() { return cardFingerprintChecked; }
    public void setCardFingerprintChecked(boolean cardFingerprintChecked) { this.cardFingerprintChecked = cardFingerprintChecked; }
    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
    public LocalDateTime getConvertedAt() { return convertedAt; }
    public void setConvertedAt(LocalDateTime convertedAt) { this.convertedAt = convertedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
