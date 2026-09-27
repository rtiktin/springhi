package com.springhi.portfolio.dto;

public record UserSignupStatusDto(Long userId, String createdAt, String planName, String status, boolean subscribed, boolean phoneVerified) {
    public UserSignupStatusDto(Long userId, String createdAt, String planName, String status, boolean subscribed) {
        this(userId, createdAt, planName, status, subscribed, true);
    }
}
