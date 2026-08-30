package com.springhi.user.service;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.PaymentMethod;
import com.springhi.user.model.SubscriptionConfig;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.PaymentHistoryRepository;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.SubscriptionConfigRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private SubscriptionConfigRepository configRepository;
    @Mock private UserSubscriptionRepository subscriptionRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private PaymentHistoryRepository paymentHistoryRepository;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private ReferralService referralService;
    @Mock private StripeService stripeService;

    @InjectMocks
    private SubscriptionService service;

    private SubscriptionConfig freeConfig;
    private SubscriptionConfig basicConfig;
    private SubscriptionConfig premiumConfig;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "portfolioServiceUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(service, "internalSecret", "test-internal");
        ReflectionTestUtils.setField(service, "cardFingerprintSecret", "test-card-fp");

        freeConfig = config("FREE", "Free", "0.00", "0.00", 3, 4);
        basicConfig = config("BASIC", "Basic", "10.00", "100.00", 10, 20);
        premiumConfig = config("PREMIUM", "Premium", "30.00", "300.00", 49, 79);

        when(configRepository.findByPlanName("FREE")).thenReturn(Optional.of(freeConfig));
        when(configRepository.findByPlanName("BASIC")).thenReturn(Optional.of(basicConfig));
        when(configRepository.findByPlanName("PREMIUM")).thenReturn(Optional.of(premiumConfig));

        when(paymentHistoryRepository.save(any(PaymentHistory.class))).thenAnswer(inv -> {
            PaymentHistory p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });
        when(subscriptionRepository.save(any(UserSubscription.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- helpers ----------

    private SubscriptionConfig config(String plan, String display, String monthly, String annual, int ports, int opts) {
        SubscriptionConfig c = new SubscriptionConfig();
        c.setPlanName(plan);
        c.setDisplayName(display);
        c.setMonthlyPrice(new BigDecimal(monthly));
        c.setAnnualPrice(new BigDecimal(annual));
        c.setMaxPortfolios(ports);
        c.setMaxOptimizationsPerMonth(opts);
        return c;
    }

    private UserSubscription sub(String plan, String cycle, String status, LocalDateTime start, LocalDateTime next) {
        UserSubscription s = new UserSubscription();
        s.setUserId(USER_ID);
        s.setPlanName(plan);
        s.setBillingCycle(cycle);
        s.setStatus(status);
        s.setStartDate(start);
        s.setNextBillingDate(next);
        return s;
    }

    private PaymentMethod pm() {
        PaymentMethod p = new PaymentMethod();
        ReflectionTestUtils.setField(p, "id", 7L);
        p.setUserId(USER_ID);
        p.setCardBrand("Visa");
        p.setCardLastFour("4242");
        p.setCardholderName("Test User");
        return p;
    }

    private PaymentHistory capturedCharge() {
        ArgumentCaptor<PaymentHistory> captor = ArgumentCaptor.forClass(PaymentHistory.class);
        verify(paymentHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertAmountEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    private void assertNoCharge() {
        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
        verify(referralService, never()).accrueCommissionOnPayment(anyLong(), anyLong(), any());
    }

    private void assertWithin(LocalDateTime value, LocalDateTime earliest, LocalDateTime latest, String label) {
        assertNotNull(value, label + " was null");
        assertFalse(value.isBefore(earliest), label + " " + value + " is before " + earliest);
        assertFalse(value.isAfter(latest), label + " " + value + " is after " + latest);
    }

    // ---------- new subscriptions ----------

    @Test
    void newSubscription_basicMonthly_fullCharge() {
        UserSubscription s = sub("FREE", null, "ACTIVE", LocalDateTime.now().minusDays(30), null);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        LocalDateTime before = LocalDateTime.now();
        Map<String, Object> res = service.subscribe(USER_ID, "BASIC", "MONTHLY", null, null, null, null, null, true);
        LocalDateTime after = LocalDateTime.now();

        assertEquals("BASIC", res.get("planName"));
        assertEquals("MONTHLY", res.get("billingCycle"));
        assertEquals("ACTIVE", res.get("status"));
        assertWithin((LocalDateTime) res.get("nextBillingDate"), before.plusMonths(1), after.plusMonths(1), "nextBillingDate");

        PaymentHistory ph = capturedCharge();
        assertEquals("BASIC", ph.getPlanName());
        assertEquals("MONTHLY", ph.getBillingCycle());
        assertAmountEquals(new BigDecimal("10.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void newSubscription_premiumAnnual_fullCharge() {
        UserSubscription s = sub("FREE", null, "ACTIVE", LocalDateTime.now().minusDays(30), null);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        LocalDateTime before = LocalDateTime.now();
        Map<String, Object> res = service.subscribe(USER_ID, "PREMIUM", "ANNUAL", null, null, null, null, null, true);
        LocalDateTime after = LocalDateTime.now();

        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("ANNUAL", res.get("billingCycle"));
        assertWithin((LocalDateTime) res.get("nextBillingDate"), before.plusYears(1), after.plusYears(1), "nextBillingDate");

        PaymentHistory ph = capturedCharge();
        assertEquals("PREMIUM", ph.getPlanName());
        assertEquals("ANNUAL", ph.getBillingCycle());
        assertAmountEquals(new BigDecimal("300.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void subscribeToFree_zeroCharge() {
        UserSubscription s = sub("FREE", null, "ACTIVE", LocalDateTime.now().minusDays(30), null);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));

        Map<String, Object> res = service.subscribe(USER_ID, "FREE", "MONTHLY", null, null, null, null, null, true);

        assertEquals("FREE", res.get("planName"));
        assertEquals("ACTIVE", res.get("status"));
        PaymentHistory ph = capturedCharge();
        assertAmountEquals(BigDecimal.ZERO, ph.getAmount());
    }

    // ---------- mid-period upgrades / downgrades / cancels ----------

    @Test
    void upgradeMidPeriod_isProratedAndKeepsPeriod() {
        LocalDateTime start = LocalDateTime.now().minusDays(15);
        LocalDateTime next = LocalDateTime.now().plusDays(15);
        UserSubscription s = sub("BASIC", "MONTHLY", "ACTIVE", start, next);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        Map<String, Object> res = service.subscribe(USER_ID, "PREMIUM", "MONTHLY", null, null, null, null, null, true);

        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("MONTHLY", res.get("billingCycle"));
        // prorated upgrade keeps the existing period end date
        assertEquals(next, res.get("nextBillingDate"));
        assertEquals(start, res.get("startDate"));

        PaymentHistory ph = capturedCharge();
        assertEquals("PREMIUM", ph.getPlanName());
        assertEquals("MONTHLY", ph.getBillingCycle());
        // (30 - 10) * 0.5 remaining = 10.00
        assertAmountEquals(new BigDecimal("10.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void downgradeMidPeriod_isDeferredNoCharge() {
        LocalDateTime next = LocalDateTime.now().plusDays(15);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", LocalDateTime.now().minusDays(15), next);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));

        Map<String, Object> res = service.subscribe(USER_ID, "BASIC", "MONTHLY", null, null, null, null, null, true);

        // plan stays on the higher tier until the period ends; change is pending
        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("BASIC", res.get("pendingPlanName"));
        assertEquals("MONTHLY", res.get("pendingBillingCycle"));
        assertEquals("ACTIVE", res.get("status"));
        assertNoCharge();
    }

    @Test
    void cancelMidPeriod_deferredToFree() {
        LocalDateTime next = LocalDateTime.now().plusDays(15);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", LocalDateTime.now().minusDays(15), next);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));

        Map<String, Object> res = service.subscribe(USER_ID, "FREE", "MONTHLY", null, null, null, null, null, true);

        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("FREE", res.get("pendingPlanName"));
        assertEquals("CANCELLED", res.get("status"));
        assertEquals(next, res.get("endDate"));
        assertNoCharge();
    }

    @Test
    void samePlanReselectWithPending_cancelsPendingNoCharge() {
        LocalDateTime next = LocalDateTime.now().plusDays(15);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", LocalDateTime.now().minusDays(15), next);
        s.setPendingPlanName("BASIC");
        s.setPendingBillingCycle("MONTHLY");
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));

        Map<String, Object> res = service.subscribe(USER_ID, "PREMIUM", "MONTHLY", null, null, null, null, null, true);

        assertEquals("PREMIUM", res.get("planName"));
        assertNull(res.get("pendingPlanName"));
        assertNull(res.get("pendingBillingCycle"));
        assertEquals("ACTIVE", res.get("status"));
        // original period is untouched
        assertEquals(next, res.get("nextBillingDate"));
        assertNoCharge();
    }

    @Test
    void cycleSwitchMonthlyToAnnual_chargesCreditAndNewAnnualPeriod() {
        LocalDateTime start = LocalDateTime.now().minusDays(15);
        LocalDateTime next = LocalDateTime.now().plusDays(15);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", start, next);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        LocalDateTime before = LocalDateTime.now();
        Map<String, Object> res = service.subscribe(USER_ID, "PREMIUM", "ANNUAL", null, null, null, null, null, true);
        LocalDateTime after = LocalDateTime.now();

        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("ANNUAL", res.get("billingCycle"));
        assertWithin((LocalDateTime) res.get("nextBillingDate"), before.plusYears(1), after.plusYears(1), "nextBillingDate");

        PaymentHistory ph = capturedCharge();
        assertEquals("PREMIUM", ph.getPlanName());
        assertEquals("ANNUAL", ph.getBillingCycle());
        // annual(300) - credit for unused half of monthly(30*0.5=15) = 285.00
        assertAmountEquals(new BigDecimal("285.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void cycleSwitchAnnualToMonthly_isDeferredNoCharge() {
        LocalDateTime next = LocalDateTime.now().plusMonths(6);
        UserSubscription s = sub("PREMIUM", "ANNUAL", "ACTIVE", LocalDateTime.now().minusMonths(6), next);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(s));

        Map<String, Object> res = service.subscribe(USER_ID, "PREMIUM", "MONTHLY", null, null, null, null, null, true);

        // paid annual period preserved; cycle flips to MONTHLY at period end
        assertEquals("PREMIUM", res.get("planName"));
        assertEquals("ANNUAL", res.get("billingCycle"));
        assertEquals("PREMIUM", res.get("pendingPlanName"));
        assertEquals("MONTHLY", res.get("pendingBillingCycle"));
        assertEquals(next, res.get("nextBillingDate"));
        assertNoCharge();
    }

    // ---------- scheduled job: applyScheduledChanges ----------

    @Test
    void scheduledAutoRenew_paidWithCard_chargesAndAdvancesExactlyOneCycle() {
        LocalDateTime oldNext = LocalDateTime.now().minusMinutes(5);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", oldNext.minusMonths(1), oldNext);
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        service.applyScheduledChanges();

        assertEquals("PREMIUM", s.getPlanName());
        assertEquals("MONTHLY", s.getBillingCycle());
        assertEquals("ACTIVE", s.getStatus());
        assertEquals(oldNext, s.getStartDate());
        assertEquals(oldNext.plusMonths(1), s.getNextBillingDate());

        PaymentHistory ph = capturedCharge();
        assertEquals("PREMIUM", ph.getPlanName());
        assertEquals("MONTHLY", ph.getBillingCycle());
        assertEquals(7L, ph.getPaymentMethodId());
        assertAmountEquals(new BigDecimal("30.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void scheduledAutoRenew_noCard_revertsToFree() {
        LocalDateTime oldNext = LocalDateTime.now().minusMinutes(5);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", oldNext.minusMonths(1), oldNext);
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.empty());

        service.applyScheduledChanges();

        assertEquals("FREE", s.getPlanName());
        assertEquals("EXPIRED", s.getStatus());
        assertNull(s.getNextBillingDate());
        assertNotNull(s.getEndDate());
        assertNoCharge();
    }

    @Test
    void scheduledPendingCancel_appliesFreeNoCharge() {
        LocalDateTime oldNext = LocalDateTime.now().minusMinutes(5);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "CANCELLED", oldNext.minusMonths(1), oldNext);
        s.setPendingPlanName("FREE");
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of(s));

        service.applyScheduledChanges();

        assertEquals("FREE", s.getPlanName());
        assertEquals("CANCELLED", s.getStatus());
        assertNull(s.getNextBillingDate());
        assertNoCharge();
    }

    @Test
    void scheduledPendingDowngrade_billsNewTier() {
        LocalDateTime oldNext = LocalDateTime.now().minusMinutes(5);
        UserSubscription s = sub("PREMIUM", "MONTHLY", "ACTIVE", oldNext.minusMonths(1), oldNext);
        s.setPendingPlanName("BASIC");
        s.setPendingBillingCycle("MONTHLY");
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        service.applyScheduledChanges();

        assertEquals("BASIC", s.getPlanName());
        assertEquals("MONTHLY", s.getBillingCycle());
        assertEquals("ACTIVE", s.getStatus());
        assertNull(s.getPendingPlanName());
        assertEquals(oldNext.plusMonths(1), s.getNextBillingDate());

        PaymentHistory ph = capturedCharge();
        assertEquals("BASIC", ph.getPlanName());
        assertEquals("MONTHLY", ph.getBillingCycle());
        assertAmountEquals(new BigDecimal("10.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void scheduledPendingCycleSwitch_billsMonthly() {
        LocalDateTime oldNext = LocalDateTime.now().minusMinutes(5);
        UserSubscription s = sub("PREMIUM", "ANNUAL", "ACTIVE", oldNext.minusYears(1), oldNext);
        s.setPendingPlanName("PREMIUM");
        s.setPendingBillingCycle("MONTHLY");
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of(s));
        when(paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(USER_ID)).thenReturn(Optional.of(pm()));

        service.applyScheduledChanges();

        assertEquals("PREMIUM", s.getPlanName());
        assertEquals("MONTHLY", s.getBillingCycle());
        assertEquals("ACTIVE", s.getStatus());
        assertEquals(oldNext.plusMonths(1), s.getNextBillingDate());

        PaymentHistory ph = capturedCharge();
        assertEquals("PREMIUM", ph.getPlanName());
        assertEquals("MONTHLY", ph.getBillingCycle());
        assertAmountEquals(new BigDecimal("30.00"), ph.getAmount());
        verify(referralService).accrueCommissionOnPayment(eq(USER_ID), anyLong(), any());
    }

    @Test
    void scheduledJob_noDueSubscriptions_doesNothing() {
        when(subscriptionRepository.findByNextBillingDateBefore(any(LocalDateTime.class))).thenReturn(List.of());

        service.applyScheduledChanges();

        verify(paymentHistoryRepository, never()).save(any(PaymentHistory.class));
        verify(referralService, never()).accrueCommissionOnPayment(anyLong(), anyLong(), any());
    }

    // sanity check that ChronoUnit import is used (kept for date math clarity)
    @SuppressWarnings("unused")
    private long secondsBetween(LocalDateTime a, LocalDateTime b) {
        return ChronoUnit.SECONDS.between(a, b);
    }
}
