package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "referral_clicks_daily",
    schema = "springhi",
    uniqueConstraints = @UniqueConstraint(columnNames = {"referral_code_id", "day"})
)
public class ReferralClickDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referral_code_id", nullable = false)
    private Long referralCodeId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "clicks", nullable = false)
    private int clicks = 0;

    @Column(name = "unique_clicks", nullable = false)
    private int uniqueClicks = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public ReferralClickDaily() {}

    public Long getId() { return id; }
    public Long getReferralCodeId() { return referralCodeId; }
    public void setReferralCodeId(Long referralCodeId) { this.referralCodeId = referralCodeId; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public int getClicks() { return clicks; }
    public void setClicks(int clicks) { this.clicks = clicks; }
    public int getUniqueClicks() { return uniqueClicks; }
    public void setUniqueClicks(int uniqueClicks) { this.uniqueClicks = uniqueClicks; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
