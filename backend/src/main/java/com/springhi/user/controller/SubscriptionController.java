package com.springhi.user.controller;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.SubscriptionConfig;
import com.springhi.user.model.User;
import com.springhi.user.service.StripeService;
import com.springhi.user.service.StripeWebhookService;
import com.springhi.user.service.SubscriptionService;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final StripeWebhookService stripeWebhookService;

    @Value("${app.internal.secret:dev-internal-secret-change-me}")
    private String internalSecret;

    @Value("${app.stripe.webhook-secret:}")
    private String webhookSecret;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  StripeService stripeService,
                                  StripeWebhookService stripeWebhookService) {
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
        this.stripeWebhookService = stripeWebhookService;
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
            @PathVariable Long userId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(subscriptionService.getLimitsForUserReadOnly(userId));
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
            String paymentMethodId = (String) body.get("paymentMethodId");

            if (planName == null || planName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "planName is required"));
            }

            if (!"FREE".equalsIgnoreCase(planName) && !((User) userDetails).isEmailVerified()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Please verify your email address before starting a subscription.",
                        "code", "EMAIL_NOT_VERIFIED"));
            }
            if (!"FREE".equalsIgnoreCase(planName)) {
                User u = (User) userDetails;
                boolean missingName = (u.getFirstName() == null || u.getFirstName().isBlank())
                        || (u.getLastName() == null || u.getLastName().isBlank());
                if (missingName) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "Please add your first and last name before starting a subscription.",
                            "code", "NAME_REQUIRED"));
                }
            }

            Map<String, Object> result = subscriptionService.subscribe(
                    userId, planName, billingCycle, cardholderName, cardNumber,
                    expiryMonth, expiryYear, billingZip, useExistingCard, paymentMethodId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
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

    // ===================== Stripe-native endpoints =====================

    @GetMapping("/stripe-config")
    public ResponseEntity<Map<String, Object>> getStripeConfig() {
        return ResponseEntity.ok(subscriptionService.getStripePublicConfig());
    }

    @PostMapping("/setup-intent")
    public ResponseEntity<?> createSetupIntent(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        User user = (User) userDetails;
        Long userId = user.getId();
        try {
            String clientSecret = subscriptionService.createSetupIntent(userId);
            // Prefill the PaymentElement's billing details from the account so the user doesn't
            // retype them. The Element is created with fields.billingDetails='auto', so it renders
            // the fields it needs; these defaults pre-populate name/email/phone when present.
            Map<String, String> billing = new LinkedHashMap<>();
            String first = user.getFirstName();
            String last = user.getLastName();
            String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            if (!full.isBlank()) billing.put("name", full);
            String email = user.getEmail();
            if (email != null && !email.isBlank()) billing.put("email", email);
            String phone = user.getPhone();
            if (phone != null && !phone.isBlank()) billing.put("phone", phone);
            return ResponseEntity.ok(Map.of(
                    "clientSecret", clientSecret,
                    "billingDetails", billing));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/confirm-payment-method")
    public ResponseEntity<?> confirmPaymentMethod(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return ResponseEntity.status(403).build();
        Long userId = ((User) userDetails).getId();
        String paymentMethodId = (String) body.get("paymentMethodId");
        try {
            return ResponseEntity.ok(subscriptionService.confirmPaymentMethod(userId, paymentMethodId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Stripe webhook. Unauthenticated (verified by signature). Raw body is required for signature
     * verification, so this reads the payload as a plain string.
     */
    @PostMapping(value = "/webhook", produces = "application/json")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank() || webhookSecret.contains("REPLACE_ME")) {
            log.warn("Stripe webhook received but webhook-secret not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"message\":\"webhook not configured\"}");
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\":\"missing signature\"}");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"message\":\"invalid signature\"}");
        }
        stripeWebhookService.handleEvent(event);
        return ResponseEntity.ok("{}");
    }

    // ===================== Sandbox test helpers (admin only) =====================

    private boolean isAdmin(UserDetails userDetails) {
        if (userDetails instanceof User u) {
            return u.getUserType() == 10;
        }
        return false;
    }

    @PostMapping("/test/subscribe")
    public ResponseEntity<?> testSubscribe(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        Long userId = body.get("userId") instanceof Number ? ((Number) body.get("userId")).longValue() : null;
        String plan = (String) body.get("planName");
        String cycle = (String) body.getOrDefault("billingCycle", "MONTHLY");
        boolean fail = Boolean.TRUE.equals(body.get("failPayment"));
        if (userId == null || plan == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "userId and planName are required"));
        }
        try {
            return ResponseEntity.ok(subscriptionService.runTestSubscribe(userId, plan, cycle, fail));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/test/clock")
    public ResponseEntity<?> createTestClock(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        long frozen = body.get("frozenTime") instanceof Number
                ? ((Number) body.get("frozenTime")).longValue()
                : System.currentTimeMillis() / 1000L;
        try {
            com.stripe.model.testhelpers.TestClock clock = stripeService.createTestClock(frozen);
            return ResponseEntity.ok(Map.of("testClockId", clock.getId(), "frozenTime", frozen));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/test/clock/{id}/advance")
    public ResponseEntity<?> advanceTestClock(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        long frozen = body.get("frozenTime") instanceof Number
                ? ((Number) body.get("frozenTime")).longValue()
                : System.currentTimeMillis() / 1000L;
        try {
            return ResponseEntity.ok(subscriptionService.advanceTestClock(id, frozen));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    /** Admin-only: delete Stripe + DB rows created by StripeSandboxIT / the test-subscribe flow. */
    @PostMapping("/test/cleanup")
    public ResponseEntity<?> cleanupSandboxData(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        if (!stripeService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "Stripe is not enabled."));
        }
        try {
            return ResponseEntity.ok(stripeService.cleanupSandboxTestData());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    /** Admin-only: volatile snapshot of the most recently received Stripe webhook event. */
    @GetMapping("/webhook/last")
    public ResponseEntity<Map<String, Object>> lastWebhook(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        Map<String, Object> out = new java.util.LinkedHashMap<>(stripeWebhookService.getLastWebhookStatus());
        out.put("enabled", stripeService.isEnabled());
        out.put("configured", webhookSecret != null && !webhookSecret.isBlank() && !webhookSecret.contains("REPLACE_ME"));
        return ResponseEntity.ok(out);
    }

    /** Admin-only: read the effective Stripe Link toggle + whether the account is in live mode. */
    @GetMapping("/stripe-link")
    public ResponseEntity<?> getStripeLink(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("liveMode", stripeService.isLiveMode());
        out.put("linkEnabled", stripeService.isLinkEnabledEffective());
        return ResponseEntity.ok(out);
    }

    /** Admin-only: turn Stripe Link on/off (test and live mode; test mode defaults off). */
    @PostMapping("/stripe-link")
    public ResponseEntity<?> setStripeLink(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || !isAdmin(userDetails)) return ResponseEntity.status(403).build();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        try {
            boolean effective = stripeService.setLinkEnabled(enabled);
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("liveMode", stripeService.isLiveMode());
            out.put("linkEnabled", effective);
            return ResponseEntity.ok(out);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
