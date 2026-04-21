package com.springhi.portfolio.controller;

import com.springhi.portfolio.dto.UserProfileRequest;
import com.springhi.portfolio.dto.UserProfileResponse;
import com.springhi.portfolio.security.UserPrincipal;
import com.springhi.portfolio.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return userProfileService.getProfile(principal.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> saveProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UserProfileRequest request) {
        return ResponseEntity.ok(userProfileService.saveProfile(principal.getId(), request));
    }
}
