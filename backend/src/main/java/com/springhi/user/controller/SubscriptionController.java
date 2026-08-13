package com.springhi.user.controller;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.SubscriptionConfig;
import com.springhi.user.model.User;
import com.springhi.user.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionConfig>> getPlans() {
        return ResponseEntity.ok(subscriptionService.getAllPlans());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(subscriptionService.getStatus(userId));
    }

    @GetMapping("/limits")
    public ResponseEntity<Map<String, Object>> getLimits(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(subscriptionService.getLimitsForUser(userId));
    }

    @GetMapping("/limits/{userId}")
    public ResponseEntity<Map<String, Object>> getLimitsByUserId(
            @PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.getLimitsForUser(userId));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        try {
            String planName = (String) body.get("planName");
            String billingCycle = (String) body.getOrDefault("billingCycle", "MONTHLY");
            String cardholderName = (String) body.get("cardholderName");
            String cardNumber = (String) body.get("cardNumber");
            Integer expiryMonth = body.get("expiryMonth") instanceof Number
                    ? ((Number) body.get("expiryMonth")).intValue() : null;
            Integer expiryYear = body.get("expiryYear") instanceof Number
                    ? ((Number) body.get("expiryYear")).intValue() : null;
            String billingZip = (String) body.get("billingZip");
            boolean useExistingCard = Boolean.TRUE.equals(body.get("useExistingCard"));

            if (planName == null || planName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "planName is required"));
            }
            if (!"FREE".equalsIgnoreCase(planName) && !useExistingCard && (cardNumber == null || cardNumber.isBlank())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Card number is required for paid plans"));
            }

            Map<String, Object> result = subscriptionService.subscribe(
                    userId, planName, billingCycle, cardholderName, cardNumber,
                    expiryMonth, expiryYear, billingZip, useExistingCard);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/payment-method")
    public ResponseEntity<?> addPaymentMethod(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        try {
            String cardholderName = (String) body.get("cardholderName");
            String cardNumber = (String) body.get("cardNumber");
            Integer expiryMonth = body.get("expiryMonth") instanceof Number
                    ? ((Number) body.get("expiryMonth")).intValue() : null;
            Integer expiryYear = body.get("expiryYear") instanceof Number
                    ? ((Number) body.get("expiryYear")).intValue() : null;
            String billingZip = (String) body.get("billingZip");
            if (cardNumber == null || cardNumber.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Card number is required"));
            }
            Map<String, Object> result = subscriptionService.addPaymentMethod(
                    userId, cardholderName, cardNumber, expiryMonth, expiryYear, billingZip);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        subscriptionService.cancel(userId);
        return ResponseEntity.ok(Map.of("message", "Subscription cancelled"));
    }

    @GetMapping("/payment-history")
    public ResponseEntity<List<PaymentHistory>> getPaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        return ResponseEntity.ok(subscriptionService.getPaymentHistory(userId));
    }
}
