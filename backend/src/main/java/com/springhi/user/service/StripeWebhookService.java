package com.springhi.user.service;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.PaymentMethod;
import com.springhi.user.model.UserSubscription;
import com.springhi.user.repository.PaymentHistoryRepository;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mirrors Stripe subscription lifecycle events into the local {@code UserSubscription} /
 * {@code PaymentHistory} tables and accrues referral commission on successful payments.
 * Stripe owns the billing cycle in Stripe-native mode; this is the read model projector.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final ReferralService referralService;
    private final StripeService stripeService;
    private final SubscriptionService subscriptionService;

    // Volatile "last webhook received" snapshot for the Admin Stripe indicator (not persisted).
    private volatile String lastEventType;
    private volatile String lastEventId;
    private volatile LocalDateTime lastEventAt;
    private final AtomicLong totalEvents = new AtomicLong(0);

    public StripeWebhookService(UserSubscriptionRepository subscriptionRepository,
                                PaymentHistoryRepository paymentHistoryRepository,
                                PaymentMethodRepository paymentMethodRepository,
                                ReferralService referralService,
                                StripeService stripeService,
                                SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.referralService = referralService;
        this.stripeService = stripeService;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public void handleEvent(Event event) {
        if (event == null || event.getType() == null) {
            log.warn("Stripe webhook: null event/type received");
            return;
        }
        String type = event.getType();
        log.info("Stripe webhook event: type={} id={}", type, event.getId());
        this.lastEventType = type;
        this.lastEventId = event.getId();
        this.lastEventAt = LocalDateTime.now();
        this.totalEvents.incrementAndGet();
        try {
            switch (type) {
                case "invoice.paid", "invoice.payment_succeeded" -> onInvoicePaid(event);
                case "invoice.payment_failed" -> onInvoiceFailed(event);
                case "customer.subscription.deleted" -> onSubscriptionDeleted(event);
                case "customer.subscription.updated" -> onSubscriptionUpdated(event);
                case "charge.refunded" -> onChargeRefunded(event);
                case "charge.dispute.closed" -> onDisputeClosed(event);
                default -> log.debug("Stripe webhook: ignoring unhandled event type {}", type);
            }
        } catch (Exception e) {
            log.error("Stripe webhook: failed handling event type={} id={}: {}", type, event.getId(), e.getMessage(), e);
        }
    }

    private void onInvoicePaid(Event event) {
        Invoice invoice = asObject(event, Invoice.class);
        if (invoice == null) return;
        String customerId = invoice.getCustomer();
        Optional<UserSubscription> subOpt = subscriptionRepository.findByStripeCustomerId(customerId);
        if (subOpt.isEmpty()) {
            log.warn("Stripe invoice.paid: no local subscription for customerId={} (invoice={})", customerId, invoice.getId());
            return;
        }
        UserSubscription sub = subOpt.get();
        Long cents = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : invoice.getTotal();
        BigDecimal amount = cents != null
                ? BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String reason = invoice.getBillingReason();

        // The invoice is the source of truth for which plan/cycle was paid for; mirror it locally.
        String planCycle = planCycleFromInvoice(invoice);
        if (planCycle != null) {
            String[] pc = planCycle.split(":");
            sub.setPlanName(pc[0]);
            if (pc.length > 1) sub.setBillingCycle(pc[1]);
        }

        LocalDateTime periodStart = toLdt(invoice.getPeriodStart());
        LocalDateTime periodEnd = toLdt(invoice.getPeriodEnd());
        LocalDateTime nextBilling = toLdt(invoice.getNextPaymentAttempt() != null
                ? invoice.getNextPaymentAttempt() : invoice.getPeriodEnd());
        // The first invoice of a subscription (billingReason=subscription_create) has a degenerate
        // [t0,t0] period: periodEnd == periodStart == t0 and nextPaymentAttempt is null, so the raw
        // value would set nextBillingDate to "today" and leave it there for a whole cycle. Derive the
        // real next billing (first renewal) as periodStart + one cycle.
        if (periodStart != null && periodEnd != null && periodEnd.isEqual(periodStart)
                && invoice.getNextPaymentAttempt() == null) {
            nextBilling = "ANNUAL".equalsIgnoreCase(sub.getBillingCycle())
                    ? periodStart.plusYears(1) : periodStart.plusMonths(1);
        }
        log.info("Stripe invoice.paid timing: invoiceId={} reason={} nextPaymentAttempt={} periodStart={} periodEnd={} -> nextBilling={}",
                invoice.getId(), reason, invoice.getNextPaymentAttempt(), invoice.getPeriodStart(), invoice.getPeriodEnd(), nextBilling);

        sub.setStatus("ACTIVE");
        sub.setEndDate(null);
        sub.setPendingPlanName(null);
        sub.setPendingBillingCycle(null);
        if (periodStart != null) sub.setStartDate(periodStart);
        if (nextBilling != null) sub.setNextBillingDate(nextBilling);
        subscriptionRepository.save(sub);

        if (amount.signum() > 0) {
            // Stripe may redeliver a webhook event on retry; record the payment + commission only once
            // per Stripe invoice id so retries (and the sandbox IT's manual event replay) are idempotent.
            if (invoice.getId() != null && paymentHistoryRepository.existsByStripeInvoiceId(invoice.getId())) {
                log.info("Stripe invoice.paid already recorded (idempotent skip): userId={} invoiceId={} amount={} reason={} nextBilling={}",
                        sub.getUserId(), invoice.getId(), amount, reason, nextBilling);
            } else {
                PaymentHistory ph = new PaymentHistory();
                ph.setUserId(sub.getUserId());
                ph.setPlanName(sub.getPlanName());
                ph.setAmount(amount);
                ph.setBillingCycle(sub.getBillingCycle());
                ph.setStatus("COMPLETED");
                if (invoice.getId() != null) ph.setStripeInvoiceId(invoice.getId());
                PaymentMethod pm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(sub.getUserId()).orElse(null);
                if (pm != null) ph.setPaymentMethodId(pm.getId());
                paymentHistoryRepository.save(ph);
                // Referral fee basis = invoice subtotal excluding tax, after discounts (not gross
                // amountPaid, which includes tax). Fall back to subtotal, then amountPaid, if absent.
                Long basisCents = invoice.getTotalExcludingTax() != null
                        ? invoice.getTotalExcludingTax()
                        : (invoice.getSubtotal() != null ? invoice.getSubtotal() : invoice.getAmountPaid());
                BigDecimal basis = basisCents != null
                        ? BigDecimal.valueOf(basisCents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
                        : amount;
                referralService.accrueCommissionOnPayment(sub.getUserId(), ph.getId(), invoice.getId(), basis, sub.getBillingCycle());
                log.info("Stripe invoice.paid recorded: userId={} plan={} cycle={} amount={} basis={} reason={} nextBilling={} invoiceId={}",
                        sub.getUserId(), sub.getPlanName(), sub.getBillingCycle(), amount, basis, reason, nextBilling, invoice.getId());
            }
        }

        subscriptionService.enforcePlanLimits(sub.getUserId(), sub.getPlanName());
    }

    private void onInvoiceFailed(Event event) {
        Invoice invoice = asObject(event, Invoice.class);
        if (invoice == null) return;
        String customerId = invoice.getCustomer();
        subscriptionRepository.findByStripeCustomerId(customerId).ifPresent(sub -> {
            sub.setStatus("PAST_DUE");
            subscriptionRepository.save(sub);
            log.warn("Stripe invoice.payment_failed: userId={} subscription={} marked PAST_DUE (invoice={})",
                    sub.getUserId(), sub.getStripeSubscriptionId(), invoice.getId());
        });
    }

    /**
     * A refund on a referred user's subscription charge reverses the referral fee that invoice
     * generated. The charge carries the originating invoice id; clawbackCommission is idempotent and
     * full (not pro-rated), so repeated charge.refunded deliveries or multiple partial refunds on the
     * same invoice do not double-clawback. Charges not tied to an invoice (direct charges) are skipped.
     */
    private void onChargeRefunded(Event event) {
        Charge charge = asObject(event, Charge.class);
        if (charge == null) return;
        String invoiceId = resolveInvoiceId(charge);
        if (invoiceId == null || invoiceId.isBlank()) {
            log.debug("Stripe charge.refunded: no invoice on charge={} (not a subscription charge); no clawback", charge.getId());
            return;
        }
        referralService.clawbackCommission(invoiceId, "REFUND");
        log.info("Stripe charge.refunded processed: chargeId={} invoiceId={}", charge.getId(), invoiceId);
    }

    /**
     * A lost chargeback (dispute closed, status=lost) is treated like a refund: claw back the fee.
     * The Dispute object only carries the charge id (not expanded), so retrieve the charge to resolve
     * its invoice. Won/open disputes do not claw back.
     */
    private void onDisputeClosed(Event event) {
        Dispute dispute = asObject(event, Dispute.class);
        if (dispute == null) return;
        if (!"lost".equalsIgnoreCase(dispute.getStatus())) {
            log.debug("Stripe charge.dispute.closed: dispute={} status={} (not lost); no clawback", dispute.getId(), dispute.getStatus());
            return;
        }
        String chargeId = dispute.getCharge();
        if (chargeId == null || chargeId.isBlank()) return;
        Charge charge;
        try {
            charge = Charge.retrieve(chargeId);
        } catch (Exception e) {
            log.warn("Stripe charge.dispute.closed: could not retrieve charge={} to resolve invoice: {}", chargeId, e.getMessage());
            return;
        }
        String invoiceId = resolveInvoiceId(charge);
        if (invoiceId == null || invoiceId.isBlank()) {
            log.debug("Stripe charge.dispute.closed: no invoice on charge={} (not a subscription charge); no clawback", chargeId);
            return;
        }
        referralService.clawbackCommission(invoiceId, "DISPUTE");
        log.info("Stripe charge.dispute.closed (lost) processed: disputeId={} chargeId={} invoiceId={}", dispute.getId(), chargeId, invoiceId);
    }

    private void onSubscriptionDeleted(Event event) {
        Subscription subscription = asObject(event, Subscription.class);
        if (subscription == null) return;
        Optional<UserSubscription> subOpt = subscription.getCustomer() != null
                ? subscriptionRepository.findByStripeCustomerId(subscription.getCustomer())
                : subscriptionRepository.findByStripeSubscriptionId(subscription.getId());
        if (subOpt.isEmpty()) {
            log.warn("Stripe subscription.deleted: no local subscription for id={} customer={}",
                    subscription.getId(), subscription.getCustomer());
            return;
        }
        UserSubscription sub = subOpt.get();
        LocalDateTime now = LocalDateTime.now();
        sub.setPlanName("FREE");
        sub.setStatus("CANCELLED");
        sub.setEndDate(now);
        sub.setNextBillingDate(null);
        sub.setPendingPlanName(null);
        sub.setPendingBillingCycle(null);
        sub.setStripeSubscriptionId(null);
        subscriptionRepository.save(sub);
        subscriptionService.enforcePlanLimits(sub.getUserId(), "FREE");
        log.info("Stripe subscription.deleted: userId={} reverted to FREE", sub.getUserId());
    }

    private void onSubscriptionUpdated(Event event) {
        Subscription subscription = asObject(event, Subscription.class);
        if (subscription == null) return;
        Optional<UserSubscription> subOpt = subscription.getCustomer() != null
                ? subscriptionRepository.findByStripeCustomerId(subscription.getCustomer())
                : subscriptionRepository.findByStripeSubscriptionId(subscription.getId());
        if (subOpt.isEmpty()) {
            log.warn("Stripe subscription.updated: no local subscription for id={} customer={}",
                    subscription.getId(), subscription.getCustomer());
            return;
        }
        UserSubscription sub = subOpt.get();
        boolean changed = false;

        // Reflect a scheduled cancel-at-period-end.
        if (Boolean.TRUE.equals(subscription.getCancelAtPeriodEnd())) {
            if (!"CANCELLED".equalsIgnoreCase(sub.getStatus())) {
                sub.setStatus("CANCELLED");
                changed = true;
            }
            if (sub.getPendingPlanName() == null) {
                sub.setPendingPlanName("FREE");
                changed = true;
            }
            if (sub.getEndDate() == null && sub.getNextBillingDate() != null) {
                sub.setEndDate(sub.getNextBillingDate());
                changed = true;
            }
        } else if ("CANCELLED".equalsIgnoreCase(sub.getStatus())) {
            // Resumed after previously scheduling cancellation.
            sub.setStatus("ACTIVE");
            sub.setPendingPlanName(null);
            sub.setEndDate(null);
            changed = true;
        }

        // Reflect a price/plan change that just took effect (e.g. a NONE-proration downgrade at period boundary).
        String planCycle = currentPlanCycle(subscription);
        if (planCycle != null) {
            String[] pc = planCycle.split(":");
            String plan = pc[0];
            String cycle = pc.length > 1 ? pc[1] : sub.getBillingCycle();
            if (!plan.equalsIgnoreCase(sub.getPlanName())) {
                sub.setPlanName(plan);
                changed = true;
            }
            if (cycle != null && !cycle.equalsIgnoreCase(sub.getBillingCycle())) {
                sub.setBillingCycle(cycle);
                changed = true;
            }
        }

        if (changed) {
            subscriptionRepository.save(sub);
            subscriptionService.enforcePlanLimits(sub.getUserId(), sub.getPlanName());
            log.info("Stripe subscription.updated mirrored: userId={} plan={} cycle={} status={} cancelAtPeriodEnd={}",
                    sub.getUserId(), sub.getPlanName(), sub.getBillingCycle(), sub.getStatus(),
                    subscription.getCancelAtPeriodEnd());
        }
    }

    private String currentPlanCycle(Subscription subscription) {
        try {
            if (subscription.getItems() == null || subscription.getItems().getData() == null
                    || subscription.getItems().getData().isEmpty()) {
                return null;
            }
            SubscriptionItem item = subscription.getItems().getData().get(0);
            if (item.getPrice() == null) return null;
            return stripeService.planCycleForPriceId(item.getPrice().getId());
        } catch (Exception e) {
            log.warn("Could not read current price from subscription: {}", e.getMessage());
            return null;
        }
    }

    /** Derive "PLAN:CYCLE" from an invoice's first line-item price; null if it cannot be determined. */
    private String planCycleFromInvoice(Invoice invoice) {
        try {
            if (invoice.getLines() == null || invoice.getLines().getData() == null
                    || invoice.getLines().getData().isEmpty()) {
                return null;
            }
            InvoiceLineItem line = invoice.getLines().getData().get(0);
            var pricing = line.getPricing();
            if (pricing == null) return null;
            var details = pricing.getPriceDetails();
            if (details == null) return null;
            String priceId = details.getPrice();
            if (priceId == null) return null;
            return stripeService.planCycleForPriceId(priceId);
        } catch (Exception e) {
            log.warn("Could not read plan from invoice {}: {}", invoice.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the originating invoice id for a charge. stripe-java 32.2.0 exposes no
     * {@code Charge.getInvoice()} (Charges are deprecated; the invoice link is in the raw object
     * JSON only), so read the {@code invoice} string from the charge's raw JsonObject. Webhook-
     * deserialized charges may have a null raw JSON, so fall back to retrieving the charge.
     */
    private String resolveInvoiceId(Charge charge) {
        if (charge == null) return null;
        String invoiceId = readInvoiceRaw(charge.getRawJsonObject());
        if (invoiceId != null) return invoiceId;
        if (charge.getId() == null || charge.getId().isBlank()) return null;
        try {
            Charge fetched = Charge.retrieve(charge.getId());
            return readInvoiceRaw(fetched.getRawJsonObject());
        } catch (Exception e) {
            log.warn("Stripe clawback: could not retrieve charge={} to resolve invoice: {}", charge.getId(), e.getMessage());
            return null;
        }
    }

    private String readInvoiceRaw(JsonObject raw) {
        if (raw == null) return null;
        JsonElement inv = raw.get("invoice");
        if (inv == null || inv.isJsonNull() || !inv.isJsonPrimitive()) return null;
        return inv.getAsString();
    }

    private <T extends StripeObject> T asObject(Event event, Class<T> type) {
        EventDataObjectDeserializer d = event.getDataObjectDeserializer();
        if (d == null) return null;
        try {
            StripeObject obj = d.deserializeUnsafe();
            if (obj == null) {
                log.warn("Stripe webhook {}: deserialized object is null", event.getType());
                return null;
            }
            if (!type.isInstance(obj)) {
                log.warn("Stripe webhook {}: expected {} but got {}", event.getType(), type.getSimpleName(),
                        obj.getClass().getSimpleName());
                return null;
            }
            return type.cast(obj);
        } catch (Exception e) {
            log.warn("Stripe webhook {}: failed to deserialize object: {}", event.getType(), e.getMessage());
            return null;
        }
    }

    private LocalDateTime toLdt(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    /** Volatile snapshot of the most recently received webhook event, for the Admin Stripe indicator. */
    public Map<String, Object> getLastWebhookStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lastType", lastEventType);
        out.put("lastEventId", lastEventId);
        out.put("lastEventAt", lastEventAt);
        out.put("totalEvents", totalEvents.get());
        return out;
    }
}
