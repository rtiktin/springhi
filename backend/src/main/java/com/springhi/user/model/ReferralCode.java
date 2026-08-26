package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_codes", schema = "springhi")
public class ReferralCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "clicks_count", nullable = false)
    private long clicksCount = 0;

    @Column(name = "unique_clicks_count", nullable = false)
    private long uniqueClicksCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ReferralCode() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getClicksCount() { return clicksCount; }
    public void setClicksCount(long clicksCount) { this.clicksCount = clicksCount; }
    public long getUniqueClicksCount() { return uniqueClicksCount; }
    public void setUniqueClicksCount(long uniqueClicksCount) { this.uniqueClicksCount = uniqueClicksCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
