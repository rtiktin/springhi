package com.springhi.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_phone_history", schema = "springhi")
public class UserPhoneHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private LocalDateTime replacedAt;

    public UserPhoneHistory() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getReplacedAt() { return replacedAt; }
    public void setReplacedAt(LocalDateTime replacedAt) { this.replacedAt = replacedAt; }
}
