package com.springhi.user.service;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.PaymentMethod;
import com.springhi.user.model.SubscriptionConfig;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.PaymentHistoryRepository;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.SubscriptionConfigRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionService {

    private final SubscriptionConfigRepository configRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    public SubscriptionService(SubscriptionConfigRepository configRepository,
                               UserSubscriptionRepository subscriptionRepository,
                               PaymentMethodRepository paymentMethodRepository,
                               PaymentHistoryRepository paymentHistoryRepository) {
        this.configRepository = configRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
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

    @Transactional
    public Map<String, Object> subscribe(Long userId, String planName, String billingCycle,
                                         String cardholderName, String cardNumber,
                                         Integer expiryMonth, Integer expiryYear,
                                         String billingZip, boolean useExistingCard) {
        String plan = planName.toUpperCase();
        SubscriptionConfig config = getPlan(plan);

        PaymentMethod savedPm;

        if (useExistingCard) {
            savedPm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(userId)
                    .orElseThrow(() -> new IllegalArgumentException("No existing payment method found."));
        } else {
            String brand = detectCardBrand(cardNumber);
            String lastFour = cardNumber != null && cardNumber.length() >= 4
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
            pm.setDefault(true);
            savedPm = paymentMethodRepository.save(pm);
        }

        BigDecimal amount = "ANNUAL".equalsIgnoreCase(billingCycle)
                ? config.getAnnualPrice()
                : config.getMonthlyPrice();

        PaymentHistory ph = new PaymentHistory();
        ph.setUserId(userId);
        ph.setPlanName(plan);
        ph.setAmount(amount);
        ph.setBillingCycle(billingCycle);
        ph.setStatus("COMPLETED");
        ph.setPaymentMethodId(savedPm.getId());
        paymentHistoryRepository.save(ph);

        UserSubscription sub = getOrCreateSubscription(userId);
        sub.setPlanName(plan);
        sub.setBillingCycle(billingCycle);
        sub.setStatus("ACTIVE");
        sub.setStartDate(LocalDateTime.now());
        sub.setNextBillingDate("ANNUAL".equalsIgnoreCase(billingCycle)
                ? LocalDateTime.now().plusYears(1)
                : LocalDateTime.now().plusMonths(1));
        subscriptionRepository.save(sub);

        return buildStatusResponse(sub, config, savedPm);
    }

    @Transactional
    public void cancel(Long userId) {
        subscriptionRepository.findByUserId(userId).ifPresent(sub -> {
            sub.setStatus("CANCELLED");
            sub.setEndDate(sub.getNextBillingDate() != null ? sub.getNextBillingDate() : LocalDateTime.now());
            subscriptionRepository.save(sub);
        });
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
        pm.setDefault(true);
        PaymentMethod saved = paymentMethodRepository.save(pm);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardholderName", saved.getCardholderName());
        card.put("cardLastFour", saved.getCardLastFour());
        card.put("cardBrand", saved.getCardBrand());
        card.put("expiryMonth", saved.getExpiryMonth());
        card.put("expiryYear", saved.getExpiryYear());
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
}
