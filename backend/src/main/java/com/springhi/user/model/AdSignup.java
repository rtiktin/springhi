package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ad_signups",
        schema = "springhi",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"})
)
public class AdSignup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    // A user is attributed to at most one ad (first ad wins). The unique constraint on user_id
    // makes the attribution idempotent across signup retries / webhook redelivery.
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "signup_at", nullable = false)
    private LocalDateTime signupAt;

    @PrePersist
    protected void onCreate() {
        signupAt = LocalDateTime.now();
    }

    public AdSignup() {}

    public Long getId() { return id; }
    public Long getAdId() { return adId; }
    public void setAdId(Long adId) { this.adId = adId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getSignupAt() { return signupAt; }
}
