package com.springhi.user.service;

import com.springhi.user.model.AppSetting;
import com.springhi.user.model.User;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.AppSettingRepository;
import com.springhi.user.repository.PaymentHistoryRepository;
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
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final AppSettingRepository appSettingRepository;

    @Value("${app.stripe.enabled:false}")
    private boolean enabled;

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.link-enabled:true}")
    private boolean linkEnabledDefault;

    @Value("${app.stripe.price-id.BASIC_MONTHLY:}")
    private String basicMonthlyPriceId;

    @Value("${app.stripe.price-id.BASIC_ANNUAL:}")
    private String basicAnnualPriceId;

    @Value("${app.stripe.price-id.PREMIUM_MONTHLY:}")
    private String premiumMonthlyPriceId;

    @Value("${app.stripe.price-id.PREMIUM_ANNUAL:}")
    private String premiumAnnualPriceId;

    /** Persisted admin override key for the Stripe Link toggle (test and live mode). */
    private static final String LINK_ENABLED_KEY = "stripe.link_enabled";

    public StripeService(UserRepository userRepository,
                         UserSubscriptionRepository subscriptionRepository,
                         PaymentMethodRepository paymentMethodRepository,
                         PaymentHistoryRepository paymentHistoryRepository,
                         AppSettingRepository appSettingRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.appSettingRepository = appSettingRepository;
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

    /** Live (production) mode is determined by the secret-key prefix (sk_live vs sk_test). */
    public boolean isLiveMode() {
        return secretKey != null && secretKey.startsWith("sk_live");
    }

    /**
     * Effective Stripe Link toggle for the card-collection UI.
     * <ul>
     *   <li>Returns the admin-persisted override ({@link #LINK_ENABLED_KEY}) when set — works in
     *       both test and live mode so Link can be exercised in the sandbox.</li>
     *   <li>With no override: LIVE mode defaults to {@link #linkEnabledDefault} (true); DEV/test
     *       mode defaults to {@code false} so Link does not auto-fill a browser-saved card across
     *       impersonated users unless an admin explicitly enables it.</li>
     * </ul>
     */
    public boolean isLinkEnabledEffective() {
        if (!isEnabled()) {
            return false;
        }
        return appSettingRepository.findById(LINK_ENABLED_KEY)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(isLiveMode() && linkEnabledDefault);
    }

    /** Admin switch: persist the Link toggle and return the new effective value (test and live mode). */
    public boolean setLinkEnabled(boolean enabled) {
        appSettingRepository.save(new AppSetting(LINK_ENABLED_KEY, String.valueOf(enabled)));
        log.info("Admin set Stripe Link enabled={} (liveMode={})", enabled, isLiveMode());
        return isLinkEnabledEffective();
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
        String existingCustomerId = sub.getStripeCustomerId();
        if (existingCustomerId != null && !existingCustomerId.isBlank()) {
            return Customer.retrieve(existingCustomerId);
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

    /** Create a SetupIntent to collect a new card via Stripe Elements (client secret returned to FE).
     *  No customer is bound to the SetupIntent: this keeps the PaymentElement a fresh card-entry form
     *  instead of listing the customer's previously-saved cards (which would surface cards saved while
     *  impersonating, or any lingering PM, as if they belonged to this account). The confirmed PM is
     *  attached to the customer later in {@link #attachDefaultPaymentMethod}.
     *  <p>Payment method types: {@code card} always, plus {@code link} only when
     *  {@link #isLinkEnabledEffective()} is true (admin toggle on; defaults off in test mode, on in
     *  live). ACH (us_bank_account) and every other Dashboard-enabled method are excluded by
     *  omission. */
    public String createSetupIntentClientSecret(UserSubscription sub) throws StripeException {
        SetupIntentCreateParams.Builder b = SetupIntentCreateParams.builder()
                .addPaymentMethodType("card")
                .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION);
        if (isLinkEnabledEffective()) {
            b.addPaymentMethodType("link");
        }
        SetupIntent intent = SetupIntent.create(b.build());
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

    /**
     * Sandbox cleanup: delete every Stripe resource and local row created by {@code StripeSandboxIT}
     * (and the admin test-subscribe flow). Test data is identified by the {@code stripe_it_} username
     * / email prefix (users, customers) and the {@code springhi-test-} test-clock name prefix. Safe
     * to run repeatedly. Stripe exposes no PaymentMethod-delete API, so detached test-mode PMs remain
     * on the account until purged via the dashboard "Delete all test data"; everything else is removed.
     * Returns a summary map of what was removed.
     */
    public Map<String, Object> cleanupSandboxTestData() {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        int usersRemoved = 0, subsCanceled = 0, customersDeleted = 0, clocksDeleted = 0, pmsDetached = 0;
        int dbSubRows = 0, dbPmRows = 0, dbPayHistoryRows = 0;
        List<String> errors = new java.util.ArrayList<>();

        List<User> testUsers = userRepository.findByUsernameStartingWith("stripe_it_");
        for (User u : testUsers) {
            Long uid = u.getId();
            try {
                Optional<UserSubscription> subOpt = subscriptionRepository.findByUserId(uid);
                if (subOpt.isPresent()) {
                    UserSubscription sub = subOpt.get();
                    if (sub.getStripeSubscriptionId() != null) {
                        try {
                            Subscription s = Subscription.retrieve(sub.getStripeSubscriptionId());
                            if (!"canceled".equalsIgnoreCase(s.getStatus())) {
                                s.cancel();
                                subsCanceled++;
                            }
                        } catch (Exception e) {
                            errors.add("cancel sub " + sub.getStripeSubscriptionId() + ": " + e.getMessage());
                        }
                    }
                    if (sub.getStripeCustomerId() != null) {
                        try {
                            Customer.retrieve(sub.getStripeCustomerId()).delete();
                            customersDeleted++;
                        } catch (Exception e) {
                            errors.add("delete customer " + sub.getStripeCustomerId() + ": " + e.getMessage());
                        }
                    }
                    if (sub.getStripeTestClockId() != null) {
                        try {
                            com.stripe.model.testhelpers.TestClock.retrieve(sub.getStripeTestClockId()).delete();
                            clocksDeleted++;
                        } catch (Exception e) {
                            errors.add("delete clock " + sub.getStripeTestClockId() + ": " + e.getMessage());
                        }
                    }
                }

                List<com.springhi.user.model.PaymentMethod> pms =
                        paymentMethodRepository.findByUserIdOrderByCreatedAtDesc(uid);
                for (com.springhi.user.model.PaymentMethod pm : pms) {
                    String ref = pm.getCardNumberEncrypted();
                    String pmId = ref != null && ref.startsWith("stripe:") ? ref.substring("stripe:".length()) : null;
                    if (pmId != null) {
                        try {
                            com.stripe.model.PaymentMethod stripePm = com.stripe.model.PaymentMethod.retrieve(pmId);
                            if (stripePm.getCustomer() != null) {
                                stripePm.detach();
                                pmsDetached++;
                            }
                        } catch (Exception e) {
                            errors.add("detach pm " + pmId + ": " + e.getMessage());
                        }
                    }
                    paymentMethodRepository.delete(pm);
                    dbPmRows++;
                }

                List<com.springhi.user.model.PaymentHistory> history =
                        paymentHistoryRepository.findByUserIdOrderByPaymentDateDesc(uid);
                for (com.springhi.user.model.PaymentHistory ph : history) {
                    paymentHistoryRepository.delete(ph);
                    dbPayHistoryRows++;
                }

                if (subOpt.isPresent()) {
                    subscriptionRepository.delete(subOpt.get());
                    dbSubRows++;
                }

                userRepository.deleteById(uid);
                usersRemoved++;
            } catch (Exception e) {
                errors.add("user " + uid + " (" + u.getUsername() + "): " + e.getMessage());
            }
        }

        clocksDeleted += sweepOrphanedTestClocks(errors);
        int[] orphanCust = sweepOrphanedTestCustomers(errors);
        customersDeleted += orphanCust[0];
        pmsDetached += orphanCust[1];

        int dbOrphanSubRows = 0;
        for (UserSubscription orphan : subscriptionRepository.findOrphanedSubscriptions()) {
            try {
                subscriptionRepository.delete(orphan);
                dbOrphanSubRows++;
            } catch (Exception e) {
                errors.add("delete orphan sub row id=" + orphan.getId() + " userId=" + orphan.getUserId()
                        + ": " + e.getMessage());
            }
        }

        summary.put("usersRemoved", usersRemoved);
        summary.put("stripeSubscriptionsCanceled", subsCanceled);
        summary.put("stripeCustomersDeleted", customersDeleted);
        summary.put("stripeTestClocksDeleted", clocksDeleted);
        summary.put("stripePaymentMethodsDetached", pmsDetached);
        summary.put("dbSubscriptionRowsDeleted", dbSubRows);
        summary.put("dbOrphanedSubscriptionRowsDeleted", dbOrphanSubRows);
        summary.put("dbPaymentMethodRowsDeleted", dbPmRows);
        summary.put("dbPaymentHistoryRowsDeleted", dbPayHistoryRows);
        if (!errors.isEmpty()) summary.put("errors", errors);
        log.info("Sandbox cleanup: users={}, subsCanceled={}, customersDeleted={}, clocksDeleted={}, "
                        + "pmsDetached={}, dbSubRows={}, dbOrphanSubRows={}, dbPmRows={}, dbPayHistoryRows={}, errors={}",
                usersRemoved, subsCanceled, customersDeleted, clocksDeleted, pmsDetached,
                dbSubRows, dbOrphanSubRows, dbPmRows, dbPayHistoryRows, errors.size());
        return summary;
    }

    /** Delete every test clock whose name starts with {@code springhi-test-} (orphaned if its DB user is gone). */
    private int sweepOrphanedTestClocks(List<String> errors) {
        int deleted = 0;
        String startingAfter = null;
        try {
            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", 100);
                if (startingAfter != null) params.put("starting_after", startingAfter);
                var page = com.stripe.model.testhelpers.TestClock.list(params);
                var data = page.getData();
                if (data.isEmpty()) break;
                for (var c : data) {
                    if (c.getName() != null && c.getName().startsWith("springhi-test-")) {
                        try {
                            c.delete();
                            deleted++;
                        } catch (Exception e) {
                            errors.add("delete clock " + c.getId() + " (" + c.getName() + "): " + e.getMessage());
                        }
                    }
                    startingAfter = c.getId();
                }
                if (!Boolean.TRUE.equals(page.getHasMore())) break;
            }
        } catch (Exception e) {
            errors.add("sweep test clocks: " + e.getMessage());
        }
        return deleted;
    }

    /** Delete every Stripe customer whose email starts with {@code stripe_it_} (DB user already deleted). */
    private int[] sweepOrphanedTestCustomers(List<String> errors) {
        int customersDeleted = 0, pmsDetached = 0;
        String startingAfter = null;
        try {
            while (true) {
                Map<String, Object> params = new HashMap<>();
                params.put("limit", 100);
                if (startingAfter != null) params.put("starting_after", startingAfter);
                var page = Customer.list(params);
                var data = page.getData();
                if (data.isEmpty()) break;
                for (var c : data) {
                    if (c.getEmail() != null && c.getEmail().startsWith("stripe_it_")) {
                        try {
                            Customer retrieved = Customer.retrieve(c.getId());
                            // Detach surviving PMs first for an accurate detach count (customer delete also detaches).
                            try {
                                Map<String, Object> pmParams = new HashMap<>();
                                pmParams.put("customer", c.getId());
                                pmParams.put("limit", 100);
                                for (var pm : com.stripe.model.PaymentMethod.list(pmParams).getData()) {
                                    if (pm.getCustomer() != null) {
                                        pm.detach();
                                        pmsDetached++;
                                    }
                                }
                            } catch (Exception ignored) {
                                // best-effort; customer delete detaches any remaining PMs
                            }
                            retrieved.delete();
                            customersDeleted++;
                        } catch (Exception e) {
                            errors.add("delete customer " + c.getId() + ": " + e.getMessage());
                        }
                    }
                    startingAfter = c.getId();
                }
                if (!Boolean.TRUE.equals(page.getHasMore())) break;
            }
        } catch (Exception e) {
            errors.add("sweep test customers: " + e.getMessage());
        }
        return new int[]{customersDeleted, pmsDetached};
    }
}
