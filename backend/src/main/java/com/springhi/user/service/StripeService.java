package com.springhi.user.service;

import com.springhi.user.model.User;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.testhelpers.TestClockAdvanceParams;
import com.stripe.param.testhelpers.TestClockCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin wrapper over stripe-java for the subscription lifecycle in Stripe-native mode.
 * Stripe owns the billing cycle (renewals, proration, dunning); this service only creates
 * customers, collects cards via SetupIntent, and creates/updates/cancels subscriptions.
 * DB mirroring + referral commission accrual happens in the webhook handler.
 *
 * Gated by app.stripe.enabled so the legacy in-process billing path remains the fallback.
 */
@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    @Value("${app.stripe.enabled:false}")
    private boolean enabled;

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.price-id.BASIC_MONTHLY:}")
    private String basicMonthlyPriceId;

    @Value("${app.stripe.price-id.BASIC_ANNUAL:}")
    private String basicAnnualPriceId;

    @Value("${app.stripe.price-id.PREMIUM_MONTHLY:}")
    private String premiumMonthlyPriceId;

    @Value("${app.stripe.price-id.PREMIUM_ANNUAL:}")
    private String premiumAnnualPriceId;

    public StripeService(UserRepository userRepository,
                         UserSubscriptionRepository subscriptionRepository,
                         PaymentMethodRepository paymentMethodRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            if (secretKey == null || secretKey.isBlank() || secretKey.contains("REPLACE_ME")) {
                log.warn("Stripe enabled but secret-key not configured; Stripe calls will fail until set.");
            } else {
                Stripe.apiKey = secretKey;
                log.info("Stripe initialized in {} mode", secretKey.startsWith("sk_live") ? "LIVE" : "TEST");
            }
        } else {
            log.info("Stripe disabled (app.stripe.enabled=false); using legacy in-process billing.");
        }
    }

    public boolean isEnabled() {
        return enabled && secretKey != null && !secretKey.isBlank() && !secretKey.contains("REPLACE_ME");
    }

    public String priceIdFor(String plan, String cycle) {
        boolean annual = "ANNUAL".equalsIgnoreCase(cycle);
        return switch (plan.toUpperCase()) {
            case "BASIC" -> annual ? basicAnnualPriceId : basicMonthlyPriceId;
            case "PREMIUM" -> annual ? premiumAnnualPriceId : premiumMonthlyPriceId;
            default -> throw new IllegalArgumentException("No Stripe price for plan: " + plan);
        };
    }

    /** Reverse lookup: Stripe price id -> "PLAN:CYCLE" (e.g. "BASIC:MONTHLY"); null if unknown. */
    public String planCycleForPriceId(String priceId) {
        if (priceId == null) return null;
        if (priceId.equals(basicMonthlyPriceId)) return "BASIC:MONTHLY";
        if (priceId.equals(basicAnnualPriceId)) return "BASIC:ANNUAL";
        if (priceId.equals(premiumMonthlyPriceId)) return "PREMIUM:MONTHLY";
        if (priceId.equals(premiumAnnualPriceId)) return "PREMIUM:ANNUAL";
        return null;
    }

    /** Ensure a Stripe Customer exists for the user (no test clock); persist its id on the subscription row. */
    public Customer ensureCustomer(UserSubscription sub) throws StripeException {
        return ensureCustomer(sub, null);
    }

    /**
     * Ensure a Stripe Customer exists for the user. When {@code testClockId} is non-null the test
     * clock is set on the customer at creation (Stripe attaches the clock to all of the customer's
     * subscriptions; it cannot be set on a subscription directly nor added to an existing customer).
     */
    public Customer ensureCustomer(UserSubscription sub, String testClockId) throws StripeException {
        if (sub.getStripeCustomerId() != null && !sub.getStripeCustomerId().isBlank()) {
            return Customer.retrieve(sub.getStripeCustomerId());
        }
        Optional<User> userOpt = userRepository.findById(sub.getUserId());
        String name = userOpt.map(u -> {
            String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                    + (u.getLastName() == null ? "" : u.getLastName())).trim();
            return full.isBlank() ? u.getUsername() : full;
        }).orElse("User " + sub.getUserId());
        String email = userOpt.map(User::getEmail).orElse(null);

        CustomerCreateParams.Builder b = CustomerCreateParams.builder().setName(name);
        if (email != null && !email.isBlank()) b.setEmail(email);
        if (testClockId != null && !testClockId.isBlank()) b.setTestClock(testClockId);
        Customer customer = Customer.create(b.build());
        sub.setStripeCustomerId(customer.getId());
        if (testClockId != null && !testClockId.isBlank()) {
            sub.setStripeTestClockId(testClockId);
        }
        subscriptionRepository.save(sub);
        log.info("Created Stripe customer {} for userId={} testClock={}",
                customer.getId(), sub.getUserId(), testClockId);
        return customer;
    }

    /** Create a SetupIntent to collect a new card via Stripe Elements (client secret returned to FE). */
    public String createSetupIntentClientSecret(UserSubscription sub) throws StripeException {
        Customer customer = ensureCustomer(sub);
        SetupIntent intent = SetupIntent.create(SetupIntentCreateParams.builder()
                .setCustomer(customer.getId())
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                .build());
        return intent.getClientSecret();
    }

    /** Attach the confirmed PaymentMethod to the customer, set it as the invoice default, and mirror it locally. */
    public com.springhi.user.model.PaymentMethod attachDefaultPaymentMethod(UserSubscription sub, String paymentMethodId) throws StripeException {
        Customer customer = ensureCustomer(sub);
        PaymentMethod pm = PaymentMethod.retrieve(paymentMethodId);
        if (pm.getCustomer() == null) {
            pm.attach(PaymentMethodAttachParams.builder().setCustomer(customer.getId()).build());
        }
        customer.update(CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId).build())
                .build());

        PaymentMethod.Card card = pm.getCard();
        PaymentMethod.BillingDetails billing = pm.getBillingDetails();
        String brand = card != null ? card.getBrand() : "unknown";
        String last4 = card != null ? card.getLast4() : "????";
        String fingerprint = card != null ? card.getFingerprint() : null;
        int expMonth = card != null && card.getExpMonth() != null ? card.getExpMonth().intValue() : 0;
        int expYear = card != null && card.getExpYear() != null ? card.getExpYear().intValue() : 0;
        String cardholderName = billing != null && billing.getName() != null
                ? billing.getName()
                : userRepository.findById(sub.getUserId()).map(User::getUsername).orElse(null);

        paymentMethodRepository.findByUserIdOrderByCreatedAtDesc(sub.getUserId())
                .forEach(existing -> { existing.setDefault(false); paymentMethodRepository.save(existing); });

        com.springhi.user.model.PaymentMethod local = new com.springhi.user.model.PaymentMethod(); // stripe-local mirror row
        local.setUserId(sub.getUserId());
        local.setCardholderName(cardholderName);
        local.setCardLastFour(last4.length() > 4 ? last4.substring(last4.length() - 4) : last4);
        local.setCardBrand(brand);
        local.setExpiryMonth(expMonth);
        local.setExpiryYear(expYear);
        local.setCardFingerprint(fingerprint);
        local.setCardNumberEncrypted("stripe:" + paymentMethodId);
        local.setDefault(true);
        com.springhi.user.model.PaymentMethod saved = paymentMethodRepository.save(local);
        if (fingerprint != null) {
            long dup = paymentMethodRepository.countByCardFingerprintAndUserIdNot(fingerprint, sub.getUserId());
            if (dup > 0) {
                log.warn("Stripe card fingerprint {} already used by {} other user(s) besides userId={}",
                        fingerprint, dup, sub.getUserId());
            }
        }
        log.info("Attached Stripe payment method {} for userId={}", paymentMethodId, sub.getUserId());
        return saved;
    }

    /**
     * Sandbox-only helper: create a Stripe PaymentMethod from a predefined test token (e.g.
     * {@code tok_visa}, {@code tok_chargeDeclined}). Tokens are used instead of raw card numbers
     * because Stripe blocks direct card-number sends unless raw-card-data API access is enabled.
     * Used by the admin test-subscribe flow and the Stripe sandbox integration tests.
     */
    public PaymentMethod createTestPaymentMethod(String token) throws StripeException {
        Map<String, Object> card = new HashMap<>();
        card.put("token", token);
        Map<String, Object> params = new HashMap<>();
        params.put("type", "card");
        params.put("card", card);
        return PaymentMethod.create(params);
    }

    /** Create a new Stripe subscription. Returns the subscription (first invoice may require 3DS action). */
    public Subscription createSubscription(UserSubscription sub, String priceId, String testClockId) throws StripeException {
        Customer customer = ensureCustomer(sub, testClockId);
        SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                .setCustomer(customer.getId())
                .addItem(SubscriptionCreateParams.Item.builder().setPrice(priceId).setQuantity(1L).build())
                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.ALLOW_INCOMPLETE)
                .addExpand("latest_invoice.payment_intent")
                .build();
        Subscription subscription = Subscription.create(params);
        sub.setStripeSubscriptionId(subscription.getId());
        subscriptionRepository.save(sub);
        log.info("Created Stripe subscription {} for userId={} price={}", subscription.getId(), sub.getUserId(), priceId);
        return subscription;
    }

    /** Swap the single subscription item to a new price. If prorate=false the change takes effect at next period. */
    public Subscription updateSubscription(UserSubscription sub, String newPriceId, boolean prorate) throws StripeException {
        if (sub.getStripeSubscriptionId() == null) {
            throw new IllegalStateException("No Stripe subscription to update for userId=" + sub.getUserId());
        }
        Subscription current = Subscription.retrieve(sub.getStripeSubscriptionId());
        SubscriptionItem item = current.getItems().getData().get(0);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder().setId(item.getId()).setPrice(newPriceId).build())
                .setProrationBehavior(prorate
                        ? SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS
                        : SubscriptionUpdateParams.ProrationBehavior.NONE)
                .build();
        Subscription updated = current.update(params);
        log.info("Updated Stripe subscription {} to price={} prorate={} for userId={}",
                updated.getId(), newPriceId, prorate, sub.getUserId());
        return updated;
    }

    public void cancelAtPeriodEnd(UserSubscription sub) throws StripeException {
        if (sub.getStripeSubscriptionId() == null) return;
        Subscription current = Subscription.retrieve(sub.getStripeSubscriptionId());
        current.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
        log.info("Stripe subscription {} set to cancel at period end for userId={}",
                sub.getStripeSubscriptionId(), sub.getUserId());
    }

    /**
     * The PaymentIntent client secret of a subscription's first invoice, if it requires 3DS action; else null.
     * stripe-java 32.x exposes invoice payments via InvoicePaymentCollection rather than a direct
     * payment_intent getter, so 3DS handling is left to a follow-up; the sandbox test cards used here
     * do not require 3DS.
     */
    public String pendingPaymentIntentClientSecret(Subscription subscription) {
        return null;
    }

    // ---------- Test clocks (sandbox only) ----------

    public com.stripe.model.testhelpers.TestClock createTestClock(long frozenTimeEpochSeconds) throws StripeException {
        com.stripe.model.testhelpers.TestClock clock = com.stripe.model.testhelpers.TestClock.create(
                TestClockCreateParams.builder()
                        .setName("springhi-test-" + System.currentTimeMillis())
                        .setFrozenTime(frozenTimeEpochSeconds)
                        .build());
        log.info("Created Stripe test clock {} frozen at {}", clock.getId(), frozenTimeEpochSeconds);
        return clock;
    }

    public com.stripe.model.testhelpers.TestClock advanceTestClock(String testClockId, long frozenTimeEpochSeconds) throws StripeException {
        com.stripe.model.testhelpers.TestClock clock = com.stripe.model.testhelpers.TestClock.retrieve(testClockId);
        clock.advance(TestClockAdvanceParams.builder().setFrozenTime(frozenTimeEpochSeconds).build());
        log.info("Advanced Stripe test clock {} to {}", testClockId, frozenTimeEpochSeconds);
        return clock;
    }

    public List<Subscription> listSubscriptionsForCustomer(String customerId) throws StripeException {
        return Subscription.list(
                com.stripe.param.SubscriptionListParams.builder().setCustomer(customerId).build())
                .getData();
    }
}
