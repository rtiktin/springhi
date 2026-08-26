package com.springhi.user.controller;

import com.springhi.user.model.User;
import com.springhi.user.service.ReferralService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/referral")
public class ReferralController {

    private final ReferralService referralService;

    public ReferralController(ReferralService referralService) {
        this.referralService = referralService;
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<?> validateCode(@PathVariable String code) {
        return referralService.validateCode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/click")
    public ResponseEntity<?> click(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String code = body.get("code") instanceof String s ? s : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "code is required"));
        }
        referralService.recordClick(code, clientIp(req), req.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(referralService.getMyDashboard(userId));
    }

    @GetMapping("/payout-profile")
    public ResponseEntity<?> getPayoutProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(referralService.getPayoutProfile(userId));
    }

    @PutMapping("/payout-profile")
    public ResponseEntity<?> savePayoutProfile(@AuthenticationPrincipal UserDetails userDetails,
                                               @RequestBody Map<String, Object> body) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        try {
            return ResponseEntity.ok(referralService.savePayoutProfile(userId, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> adminAll(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(referralService.getAdminOverview());
    }

    @GetMapping("/admin/payouts")
    public ResponseEntity<?> adminPayouts(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(referralService.getAllPayoutsAdmin());
    }

    @PostMapping("/admin/run-payouts")
    public ResponseEntity<?> adminRunPayouts(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        referralService.runMonthlyPayouts();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/admin/payouts/{runId}/csv")
    public ResponseEntity<byte[]> adminPayoutCsv(@PathVariable String runId,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(403).build();
        String csv = referralService.generatePayoutCsv(runId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=referral-payouts-" + runId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isAdmin(UserDetails userDetails) {
        if (userDetails instanceof User u) {
            return u.getUserType() == 10;
        }
        return false;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
