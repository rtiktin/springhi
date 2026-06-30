package com.springhi.user.dto;

import com.springhi.user.model.User;
import java.time.LocalDateTime;

public record AdminUserDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        int userType,
        String userTypeName,
        LocalDateTime createdAt
) {
    public static AdminUserDto from(User u) {
        return new AdminUserDto(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getPhone(),
                u.getUserType(),
                typeName(u.getUserType()),
                u.getCreatedAt()
        );
    }

    private static String typeName(int type) {
        return switch (type) {
            case 10 -> "admin";
            case 8  -> "user";
            case 6  -> "closed";
            case 4  -> "suspended";
            default -> "unknown";
        };
    }
}
