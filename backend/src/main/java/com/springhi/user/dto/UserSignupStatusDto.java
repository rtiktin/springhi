package com.springhi.user.dto;

public record UserSignupStatusDto(
        Long userId,
        String createdAt,
        String planName,
        String status,
        boolean subscribed
) {}
