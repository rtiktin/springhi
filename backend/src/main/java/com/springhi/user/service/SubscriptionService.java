package com.springhi.user.service;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.PaymentMethod;
import com.springhi.user.model.SubscriptionConfig;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.PaymentHistoryRepository;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.SubscriptionConfigRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionConfigRepository configRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final WebClient.Builder webClientBuilder;
    private final ReferralService referralService;

    @Value("${app.portfolio-service.url:http://localhost:8081}")
    private String portfolioServiceUrl;

    @Value("${app.internal.secret:dev-internal-secret-change-me}")
    private String internalSecret;

    @Value("${app.card.fingerprint.secret:dev-card-fingerprint-secret-change-me}")
    private String cardFingerprintSecret;

    public SubscriptionService(SubscriptionConfigRepository configRepository,
                               UserSubscriptionRepository subscriptionRepository,
                               PaymentMethodRepository paymentMethodRepository,
                               PaymentHistoryRepository paymentHistoryRepository,
                               WebClient.Builder webClientBuilder,
                               ReferralService referralService) {
        this.configRepository = configRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.webClientBuilder = webClientBuilder;
        this.referralService = referralService;
    }

    public List<SubscriptionConfig> getAllPlans() {
        return configRepository.findAllByOrderByMonthlyPriceAsc();
    }

    public SubscriptionConfig getPlan(String planName) {
        return configRepository.findByPlanName(planName.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan: " + planName));
    }

    public UserSubscription getOrCreateSubscription(Long userId) {
        return subscriptionRepository.findByUserId(userId).orElseGet(() -> {
            UserSubscription sub = new UserSubscription();
            sub.setUserId(userId);
            sub.setPlanName("FREE");
            sub.setStatus("ACTIVE");
            return subscriptionRepository.save(sub);
        });
    }

    public Map<String, Object> getLimitsForUser(Long userId) {
        UserSubscription sub = getOrCreateSubscription(userId);
        SubscriptionConfig config = configRepository.findByPlanName(sub.getPlanName())
                .orElseGet(() -> configRepository.findByPlanName("FREE").orElseThrow());
        int premiumMax = configRepository.findByPlanName("PREMIUM")
                .map(SubscriptionConfig::getMaxOptimizationsPerMonth)
                .orElse(config.getMaxOptimizationsPerMonth());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planName", sub.getPlanName());
        result.put("maxPortfolios", config.getMaxPortfolios());
        result.put("maxOptimizationsPerMonth", config.getMaxOptimizationsPerMonth());
        result.put("premiumMaxOptimizationsPerMonth", premiumMax);
        return result;
    }

    public Map<String, Object> getLimitsForUserReadOnly(Long userId) {
        String planName = subscriptionRepository.findByUserId(userId)
                .map(UserSubscription::getPlanName)
                .orElse("FREE");
        SubscriptionConfig config = configRepository.findByPlanName(planName)
                .orElseGet(() -> configRepository.findByPlanName("FREE").orElseThrow());
        int premiumMax = configRepository.findByPlanName("PREMIUM")
                .map(SubscriptionConfig::getMaxOptimizationsPerMonth)
                .orElse(config.getMaxOptimizationsPerMonth());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planName", planName);
        result.put("maxPortfolios", config.getMaxPortfolios());
        result.put("maxOptimizationsPerMonth", config.getMaxOptimizationsPerMonth());
        result.put("premiumMaxOptimizationsPerMonth", premiumMax);
        return result;
    }

    @Transactional
    public Map<String, Object> subscribe(Long userId, String planName, String billingCycle,
                                         String cardholderName, String cardNumber,
                                         Integer expiryMonth, Integer expiryYear,
                                         String billingZip, boolean useExistingCard) {
        String plan = planName.toUpperCase();
        SubscriptionConfig newConfig = getPlan(plan);
        UserSubscription sub = getOrCreateSubscription(userId);
        SubscriptionConfig currentConfig = getPlan(sub.getPlanName());
        LocalDateTime now = LocalDateTime.now();

        BigDecimal currentMonthly = currentConfig.getMonthlyPrice();
        BigDecimal newMonthly = newConfig.getMonthlyPrice();
        boolean hasFuturePeriod = currentMonthly.compareTo(BigDecimal.ZERO) > 0
                && sub.getNextBillingDate() != null && sub.getNextBillingDate().isAfter(now);
        int direction = newMonthly.compareTo(currentMonthly);
        String currentCycle = sub.getBillingCycle();
        boolean cycleChanging = currentCycle != null && billingCycle != null
                && !billingCycle.equalsIgnoreCase(currentCycle);
        boolean toAnnual = "ANNUAL".equalsIgnoreCase(billingCycle);
        // Same plan, switching billing cycle mid-period (monthly <-> annual).
        // MONTHLY->ANNUAL falls through to the charge logic (a charge); ANNUAL->MONTHLY
        // is deferred below to preserve the paid annual period.
        boolean cycleUpgradeToAnnual = false;

        if (direction == 0 && hasFuturePeriod) {
            if (cycleChanging) {
                if (toAnnual) {
                    // MONTHLY -> ANNUAL: credit the unused monthly remainder and start a new
                    // annual period. The charge is computed by the charge logic below.
                    cycleUpgradeToAnnual = true;
                } else {
                    // ANNUAL -> MONTHLY: defer to period end so the paid annual period is
                    // preserved (no credit forfeited); flip to MONTHLY when it expires.
                    sub.setPendingPlanName(plan);
                    sub.setPendingBillingCycle(billingCycle);
                    subscriptionRepository.save(sub);
                    log.info("Billing cycle downgrade scheduled: userId={} plan={} {} -> {} effective at {}",
                            userId, plan, currentCycle, billingCycle, sub.getNextBillingDate());
                    PaymentMethod pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId).orElse(null);
                    return buildStatusResponse(sub, currentConfig, pm);
                }
            } else {
                // Same plan, same cycle: no charge. Cancels any pending change and keeps the
                // existing period intact (prevents a double charge when a user downgrades then
                // upgrades back to the same plan before the period ends).
                boolean hadPending = sub.getPendingPlanName() != null;
                sub.setPendingPlanName(null);
                sub.setPendingBillingCycle(null);
                if ("CANCELLED".equalsIgnoreCase(sub.getStatus())) {
                    sub.setStatus("ACTIVE");
                }
                subscriptionRepository.save(sub);
                if (hadPending) {
                    log.info("Pending subscription change cancelled, keeping current plan: userId={} plan={}",
                            userId, sub.getPlanName());
                }
                PaymentMethod pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId).orElse(null);
                return buildStatusResponse(sub, currentConfig, pm);
            }
        }

        // DOWNGRADE (incl. cancel-to-FREE) while a prepaid period is still running: defer, no charge
        if (direction < 0 && hasFuturePeriod) {
            sub.setPendingPlanName(plan);
            sub.setPendingBillingCycle(billingCycle);
            if ("FREE".equalsIgnoreCase(plan)) {
                sub.setStatus("CANCELLED");
                sub.setEndDate(sub.getNextBillingDate());
            } else {
                sub.setStatus("ACTIVE");
            }
            subscriptionRepository.save(sub);
            log.info("Downgrade scheduled: userId={} {} -> {} effective at {}",
                    userId, sub.getPlanName(), plan, sub.getNextBillingDate());
            PaymentMethod pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId).orElse(null);
            return buildStatusResponse(sub, currentConfig, pm);
        }

        boolean proratedUpgrade = direction > 0 && hasFuturePeriod;
        String effectiveCycle = proratedUpgrade ? sub.getBillingCycle() : billingCycle;

        PaymentMethod savedPm = null;
        if (!"FREE".equalsIgnoreCase(plan)) {
            if (!useExistingCard && (cardNumber == null || cardNumber.isBlank())) {
                throw new IllegalArgumentException("Card number is required for paid plans");
            }
            savedPm = resolvePaymentMethod(userId, cardholderName, cardNumber,
                    expiryMonth, expiryYear, billingZip, useExistingCard);
        }

        BigDecimal chargeAmount;
        if (proratedUpgrade) {
            BigDecimal currentCyclePrice = cyclePrice(currentConfig, sub.getBillingCycle());
            BigDecimal newCyclePrice = cyclePrice(newConfig, sub.getBillingCycle());
            long periodSec = Math.max(1L, Duration.between(sub.getStartDate(), sub.getNextBillingDate()).getSeconds());
            long remainingSec = Math.max(0L, Duration.between(now, sub.getNextBillingDate()).getSeconds());
            double fraction = Math.min(1.0, (double) remainingSec / (double) periodSec);
            BigDecimal delta = newCyclePrice.subtract(currentCyclePrice);
            chargeAmount = delta.multiply(BigDecimal.valueOf(fraction)).setScale(2, RoundingMode.HALF_UP);
            if (chargeAmount.compareTo(BigDecimal.ZERO) < 0) chargeAmount = BigDecimal.ZERO;
        } else if (cycleUpgradeToAnnual) {
            // MONTHLY -> ANNUAL on the same plan: credit the unused portion of the current
            // monthly cycle, then charge the annual price minus that credit. The period-reset
            // branch below starts a fresh annual period (billingCycle == ANNUAL here).
            BigDecimal currentCyclePrice = cyclePrice(currentConfig, sub.getBillingCycle());
            BigDecimal newCyclePrice = cyclePrice(newConfig, billingCycle);
            long periodSec = Math.max(1L, Duration.between(sub.getStartDate(), sub.getNextBillingDate()).getSeconds());
            long remainingSec = Math.max(0L, Duration.between(now, sub.getNextBillingDate()).getSeconds());
            double fraction = Math.min(1.0, (double) remainingSec / (double) periodSec);
            BigDecimal credit = currentCyclePrice.multiply(BigDecimal.valueOf(fraction)).setScale(2, RoundingMode.HALF_UP);
            chargeAmount = newCyclePrice.subtract(credit);
            if (chargeAmount.compareTo(BigDecimal.ZERO) < 0) chargeAmount = BigDecimal.ZERO;
        } else {
            chargeAmount = cyclePrice(newConfig, billingCycle);
        }

        PaymentHistory ph = new PaymentHistory();
        ph.setUserId(userId);
        ph.setPlanName(plan);
        ph.setAmount(chargeAmount);
        ph.setBillingCycle(effectiveCycle);
        ph.setStatus("COMPLETED");
        ph.setPaymentMethodId(savedPm != null ? savedPm.getId() : null);
        paymentHistoryRepository.save(ph);

        referralService.accrueCommissionOnPayment(userId, ph.getId(), chargeAmount);

        sub.setPlanName(plan);
        sub.setPendingPlanName(null);
        sub.setPendingBillingCycle(null);
        sub.setStatus("ACTIVE");
        if (proratedUpgrade) {
            // keep current period end date and billing cycle; only the plan changes
        } else {
            sub.setBillingCycle(billingCycle);
            sub.setStartDate(now);
            sub.setEndDate(null);
            sub.setNextBillingDate("ANNUAL".equalsIgnoreCase(billingCycle) ? now.plusYears(1) : now.plusMonths(1));
        }
        subscriptionRepository.save(sub);

        enforcePlanLimits(userId, plan);

        return buildStatusResponse(sub, newConfig, savedPm);
    }

    @Transactional
    public void cancel(Long userId) {
        subscriptionRepository.findByUserId(userId).ifPresent(sub -> {
            if ("FREE".equalsIgnoreCase(sub.getPlanName())) {
                return;
            }
            LocalDateTime endDate = sub.getNextBillingDate() != null ? sub.getNextBillingDate() : LocalDateTime.now();
            sub.setStatus("CANCELLED");
            sub.setEndDate(endDate);
            sub.setPendingPlanName("FREE");
            sub.setPendingBillingCycle(null);
            subscriptionRepository.save(sub);
            log.info("Subscription cancel scheduled: userId={} plan={} activeUntil={} then FREE",
                    userId, sub.getPlanName(), endDate);
        });
    }

    @Scheduled(cron = "${app.subscription.expiry-cron:0 0 * * * *}")
    @Transactional
    public void applyScheduledChanges() {
        LocalDateTime now = LocalDateTime.now();
        List<UserSubscription> due = subscriptionRepository.findByNextBillingDateBefore(now);
        if (due.isEmpty()) return;
        for (UserSubscription sub : due) {
            boolean hasPending = sub.getPendingPlanName() != null;
            String newPlan = hasPending ? sub.getPendingPlanName() : sub.getPlanName();
            String newCycle = hasPending && sub.getPendingBillingCycle() != null
                    ? sub.getPendingBillingCycle() : sub.getBillingCycle();
            sub.setPlanName(newPlan);
            sub.setPendingPlanName(null);
            sub.setPendingBillingCycle(null);
            sub.setBillingCycle(newCycle);
            if ("FREE".equalsIgnoreCase(newPlan)) {
                if (hasPending) {
                    sub.setStatus("CANCELLED");
                    sub.setEndDate(now);
                    log.info("Subscription cancelled to FREE at period end: userId={}", sub.getUserId());
                } else {
                    log.info("Clearing stale nextBillingDate for FREE subscription: userId={}", sub.getUserId());
                }
                sub.setNextBillingDate(null);
                subscriptionRepository.save(sub);
                enforcePlanLimits(sub.getUserId(), "FREE");
            } else {
                renewPaid(sub, newPlan, newCycle, now);
            }
        }
    }

    // Auto-renew a paid subscription at the end of its billing period. Charges the
    // user's default payment method for the next cycle and advances nextBillingDate
    // by exactly one cycle (so renewal times don't drift). If there is no default
    // card on file the renewal fails and the subscription reverts to FREE.
    private void renewPaid(UserSubscription sub, String plan, String cycle, LocalDateTime now) {
        PaymentMethod pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(sub.getUserId()).orElse(null);
        if (pm == null) {
            sub.setPlanName("FREE");
            sub.setStatus("EXPIRED");
            sub.setEndDate(now);
            sub.setNextBillingDate(null);
            subscriptionRepository.save(sub);
            enforcePlanLimits(sub.getUserId(), "FREE");
            log.warn("Auto-renewal failed (no payment method on file), reverted to FREE: userId={} plan={} cycle={}",
                    sub.getUserId(), plan, cycle);
            return;
        }
        SubscriptionConfig config = getPlan(plan);
        BigDecimal chargeAmount = cyclePrice(config, cycle);
        PaymentHistory ph = new PaymentHistory();
        ph.setUserId(sub.getUserId());
        ph.setPlanName(plan);
        ph.setAmount(chargeAmount);
        ph.setBillingCycle(cycle);
        ph.setStatus("COMPLETED");
        ph.setPaymentMethodId(pm.getId());
        paymentHistoryRepository.save(ph);
        referralService.accrueCommissionOnPayment(sub.getUserId(), ph.getId(), chargeAmount);
        LocalDateTime periodStart = sub.getNextBillingDate() != null ? sub.getNextBillingDate() : now;
        sub.setStatus("ACTIVE");
        sub.setStartDate(periodStart);
        sub.setEndDate(null);
        sub.setNextBillingDate("ANNUAL".equalsIgnoreCase(cycle) ? periodStart.plusYears(1) : periodStart.plusMonths(1));
        subscriptionRepository.save(sub);
        enforcePlanLimits(sub.getUserId(), plan);
        log.info("Auto-renewed subscription: userId={} plan={} cycle={} amount={} nextBillingDate={}",
                sub.getUserId(), plan, cycle, chargeAmount, sub.getNextBillingDate());
    }

    private PaymentMethod resolvePaymentMethod(Long userId, String cardholderName, String cardNumber,
                                               Integer expiryMonth, Integer expiryYear, String billingZip,
                                               boolean useExistingCard) {
        if (useExistingCard) {
            return paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                    .orElseThrow(() -> new IllegalArgumentException("No existing payment method found."));
        }
        String brand = detectCardBrand(cardNumber);
        String digits = cardNumber != null ? cardNumber.replaceAll("\\s", "") : "";
        String lastFour = digits.length() >= 4 ? digits.substring(digits.length() - 4) : "****";
        paymentMethodRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(pm -> { pm.setDefault(false); paymentMethodRepository.save(pm); });
        PaymentMethod pm = new PaymentMethod();
        pm.setUserId(userId);
        pm.setCardholderName(cardholderName);
        pm.setCardLastFour(lastFour);
        pm.setCardBrand(brand);
        pm.setExpiryMonth(expiryMonth);
        pm.setExpiryYear(expiryYear);
        pm.setBillingZip(billingZip);
        pm.setCardNumberEncrypted(maskCardNumber(cardNumber));
        pm.setCardFingerprint(cardFingerprint(cardNumber));
        pm.setDefault(true);
        PaymentMethod saved = paymentMethodRepository.save(pm);
        countOtherUsersWithCard(saved.getCardFingerprint(), userId);
        return saved;
    }

    private BigDecimal cyclePrice(SubscriptionConfig config, String billingCycle) {
        return "ANNUAL".equalsIgnoreCase(billingCycle) ? config.getAnnualPrice() : config.getMonthlyPrice();
    }

    public void enforcePlanLimits(Long userId, String targetPlan) {
        SubscriptionConfig config = getPlan(targetPlan);
        int maxPortfolios = config.getMaxPortfolios();
        int maxOptimizations = config.getMaxOptimizationsPerMonth();

        try {
            webClientBuilder.build().post()
                    .uri(portfolioServiceUrl + "/api/v1/portfolio/internal/enforce-limits")
                    .header("X-Internal-Secret", internalSecret)
                    .bodyValue(Map.of("userId", userId, "maxPortfolios", maxPortfolios, "maxOptimizationsPerMonth", maxOptimizations))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.warn("Failed to enforce plan limits for userId={} plan={}: {}", userId, targetPlan, e.getMessage());
        }
    }

    public Map<String, Object> getStatus(Long userId) {
        UserSubscription sub = getOrCreateSubscription(userId);
        SubscriptionConfig config = configRepository.findByPlanName(sub.getPlanName())
                .orElseGet(() -> configRepository.findByPlanName("FREE").orElseThrow());
        Optional<PaymentMethod> pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId);
        return buildStatusResponse(sub, config, pm.orElse(null));
    }

    public List<PaymentHistory> getPaymentHistory(Long userId) {
        return paymentHistoryRepository.findByUserIdOrderByPaymentDateDesc(userId);
    }

    @Transactional
    public Map<String, Object> addPaymentMethod(Long userId, String cardholderName, String cardNumber,
                                                Integer expiryMonth, Integer expiryYear, String billingZip) {
        String brand = detectCardBrand(cardNumber);
        String lastFour = cardNumber != null && cardNumber.replaceAll("\\s", "").length() >= 4
                ? cardNumber.replaceAll("\\s", "").substring(cardNumber.replaceAll("\\s", "").length() - 4)
                : "****";

        paymentMethodRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(pm -> { pm.setDefault(false); paymentMethodRepository.save(pm); });

        PaymentMethod pm = new PaymentMethod();
        pm.setUserId(userId);
        pm.setCardholderName(cardholderName);
        pm.setCardLastFour(lastFour);
        pm.setCardBrand(brand);
        pm.setExpiryMonth(expiryMonth);
        pm.setExpiryYear(expiryYear);
        pm.setBillingZip(billingZip);
        pm.setCardNumberEncrypted(maskCardNumber(cardNumber));
        pm.setCardFingerprint(cardFingerprint(cardNumber));
        pm.setDefault(true);
        PaymentMethod saved = paymentMethodRepository.save(pm);

        int duplicateUserCount = countOtherUsersWithCard(saved.getCardFingerprint(), userId);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardholderName", saved.getCardholderName());
        card.put("cardLastFour", saved.getCardLastFour());
        card.put("cardBrand", saved.getCardBrand());
        card.put("expiryMonth", saved.getExpiryMonth());
        card.put("expiryYear", saved.getExpiryYear());
        card.put("duplicateCard", duplicateUserCount > 0);
        card.put("duplicateUserCount", duplicateUserCount);
        return card;
    }

    @Transactional
    public SubscriptionConfig updatePlanConfig(String planName, int maxPortfolios,
                                               int maxOptimizationsPerMonth, BigDecimal monthlyPrice,
                                               BigDecimal annualPrice) {
        SubscriptionConfig config = getPlan(planName);
        config.setMaxPortfolios(maxPortfolios);
        config.setMaxOptimizationsPerMonth(maxOptimizationsPerMonth);
        if (monthlyPrice != null) config.setMonthlyPrice(monthlyPrice);
        if (annualPrice != null) config.setAnnualPrice(annualPrice);
        return configRepository.save(config);
    }

    private Map<String, Object> buildStatusResponse(UserSubscription sub, SubscriptionConfig config, PaymentMethod pm) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planName", sub.getPlanName());
        result.put("displayName", config.getDisplayName());
        result.put("billingCycle", sub.getBillingCycle());
        result.put("status", sub.getStatus());
        result.put("startDate", sub.getStartDate());
        result.put("nextBillingDate", sub.getNextBillingDate());
        result.put("endDate", sub.getEndDate());
        result.put("pendingPlanName", sub.getPendingPlanName());
        result.put("pendingBillingCycle", sub.getPendingBillingCycle());
        result.put("monthlyPrice", config.getMonthlyPrice());
        result.put("annualPrice", config.getAnnualPrice());
        result.put("maxPortfolios", config.getMaxPortfolios());
        result.put("maxOptimizationsPerMonth", config.getMaxOptimizationsPerMonth());
        if (pm != null) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("cardholderName", pm.getCardholderName());
            card.put("cardLastFour", pm.getCardLastFour());
            card.put("cardBrand", pm.getCardBrand());
            card.put("expiryMonth", pm.getExpiryMonth());
            card.put("expiryYear", pm.getExpiryYear());
            result.put("paymentMethod", card);
        }
        return result;
    }

    private String detectCardBrand(String number) {
        if (number == null) return "Unknown";
        String n = number.replaceAll("\\s", "");
        if (n.startsWith("4")) return "Visa";
        if (n.startsWith("5") || n.startsWith("2")) return "Mastercard";
        if (n.startsWith("3")) return "Amex";
        if (n.startsWith("6")) return "Discover";
        return "Unknown";
    }

    private String maskCardNumber(String number) {
        if (number == null) return null;
        String n = number.replaceAll("\\s", "");
        if (n.length() < 4) return "****";
        return "*".repeat(n.length() - 4) + n.substring(n.length() - 4);
    }

    private String cardFingerprint(String cardNumber) {
        if (cardNumber == null) return null;
        String digits = cardNumber.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cardFingerprintSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(digits.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute card fingerprint", e);
        }
    }

    private int countOtherUsersWithCard(String fingerprint, Long userId) {
        if (fingerprint == null) return 0;
        long count = paymentMethodRepository.countByCardFingerprintAndUserIdNot(fingerprint, userId);
        if (count > 0) {
            log.warn("Card fingerprint {} already used by {} other user(s) besides userId={}", fingerprint, count, userId);
        }
        return (int) count;
    }
}
