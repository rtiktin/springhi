package com.springhi.user.controller;

import com.springhi.user.dto.ProfileRequest;
import com.springhi.user.dto.ProfileResponse;
import com.springhi.user.service.UserService;
import java.security.Principal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(
            Principal principal,
            @RequestParam(required = false) String username
    ) {
        if (principal == null) {
            log.warn("GET /profile called with null principal – request is unauthenticated");
            return ResponseEntity.status(401).build();
        }
        String targetUsername = (username != null && !username.isEmpty()) ? username : principal.getName();
        log.debug("GET /profile for username={}", targetUsername);
        try {
            ProfileResponse resp = userService.getProfile(targetUsername);
            log.debug("GET /profile success for username={}", targetUsername);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("GET /profile failed for username={}: {}", targetUsername, e.getMessage(), e);
            throw e;
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            Principal principal,
            @RequestBody ProfileRequest request
    ) {
        if (principal == null) {
            log.warn("PUT /profile called with null principal – request is unauthenticated");
            return ResponseEntity.status(401).build();
        }
        log.debug("PUT /profile for username={}", principal.getName());
        try {
            ProfileResponse resp = userService.updateProfile(principal.getName(), request);
            log.debug("PUT /profile success for username={}", principal.getName());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("PUT /profile failed for username={}: {}", principal.getName(), e.getMessage(), e);
            throw e;
        }
    }
}
