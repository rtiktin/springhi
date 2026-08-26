package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_payout_profiles", schema = "springhi")
public class ReferralPayoutProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "payable_name", length = 120)
    private String payableName;

    @Column(name = "payout_email", length = 160)
    private String payoutEmail;

    @Column(name = "international", nullable = false)
    private boolean international = false;

    @Column(name = "entity_type", length = 16)
    private String entityType;

    @Column(name = "tax_id_encrypted", length = 512)
    private String taxIdEncrypted;

    @Column(name = "tax_id_last4", length = 4)
    private String taxIdLast4;

    @Column(name = "address_line1", length = 120)
    private String addressLine1;

    @Column(name = "address_line2", length = 120)
    private String addressLine2;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 60)
    private String country;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ReferralPayoutProfile() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPayableName() { return payableName; }
    public void setPayableName(String payableName) { this.payableName = payableName; }
    public String getPayoutEmail() { return payoutEmail; }
    public void setPayoutEmail(String payoutEmail) { this.payoutEmail = payoutEmail; }
    public boolean isInternational() { return international; }
    public void setInternational(boolean international) { this.international = international; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getTaxIdEncrypted() { return taxIdEncrypted; }
    public void setTaxIdEncrypted(String taxIdEncrypted) { this.taxIdEncrypted = taxIdEncrypted; }
    public String getTaxIdLast4() { return taxIdLast4; }
    public void setTaxIdLast4(String taxIdLast4) { this.taxIdLast4 = taxIdLast4; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
