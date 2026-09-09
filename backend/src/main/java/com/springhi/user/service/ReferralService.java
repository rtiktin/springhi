package com.springhi.user.service;

import com.springhi.user.model.PaymentHistory;
import com.springhi.user.model.PaymentMethod;
import com.springhi.user.model.Referral;
import com.springhi.user.model.ReferralClawback;
import com.springhi.user.model.ReferralCode;
import com.springhi.user.model.ReferralCommission;
import com.springhi.user.model.ReferralPayout;
import com.springhi.user.model.ReferralPayoutProfile;
import com.springhi.user.model.User;
import com.springhi.user.repository.PaymentHistoryRepository;
import com.springhi.user.repository.PaymentMethodRepository;
import com.springhi.user.repository.ReferralClawbackRepository;
import com.springhi.user.repository.ReferralCodeRepository;
import com.springhi.user.repository.ReferralCommissionRepository;
import com.springhi.user.repository.ReferralPayoutProfileRepository;
import com.springhi.user.repository.ReferralPayoutRepository;
import com.springhi.user.repository.ReferralRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final String STATUS_SIGNED_UP = "SIGNED_UP";
    private static final String STATUS_CONVERTED = "CONVERTED";
    // Accrual lifecycle (two-state accrual). Legacy ACCRUED/PAID rows are tolerated as LOCKED.
    private static final String COMMISSION_PENDING = "PENDING";
    private static final String COMMISSION_LOCKED = "LOCKED";
    private static final String COMMISSION_VOID = "VOID";
    private static final String COMMISSION_ACCRUED = "ACCRUED";   // legacy
    private static final String COMMISSION_PAID = "PAID";         // legacy
    private static final String REASON_REFUND = "REFUND";
    private static final String REASON_DISPUTE = "DISPUTE";
    private static final Set<String> REALIZED_STATUSES =
            Set.of(COMMISSION_LOCKED, COMMISSION_ACCRUED, COMMISSION_PAID);
    private static final Set<String> CONFIRMED_PAYOUT_STATUSES = Set.of("COMPLETED", "CONFIRMED");
    private static final java.util.regex.Pattern EMAIL_RE =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final ReferralCommissionRepository referralCommissionRepository;
    private final ReferralClawbackRepository clawbackRepository;
    private final ReferralClickFlusher clickFlusher;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final ReferralPayoutRepository referralPayoutRepository;
    private final ReferralPayoutProfileRepository payoutProfileRepository;
    private final TaxIdEncryptor taxIdEncryptor;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.referral.base-url:http://localhost:5173}")
    private String referralBaseUrl;

    @Value("${app.referral.commission-rate:0.30}")
    private double commissionRate;

    @Value("${app.referral.payout-threshold:50.00}")
    private double payoutThreshold;

    @Value("${app.referral.hold-days:31}")
    private int holdDays;

    @Value("${app.referral.min-live-referred:2}")
    private int minLiveReferred;

    @Value("${app.referral.referral-window-months:12}")
    private int referralWindowMonths;

    @Value("${app.referral.payout-method:csv}")
    private String payoutMethod;

    // ISO-3166 countries whose referrers are paid via Stripe Connect (when payout-method=connect).
    // Everyone else is paid via the CSV path. Accrual is NOT country-gated — a referrer accrues a
    // balance regardless of country; country only selects the payout rail at payout time.
    @Value("${app.referral.connect-countries:US}")
    private String connectCountriesCsv;

    private final ConcurrentHashMap<String, ClickBucket> clickBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDate> uniqueSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReferralCode> codeCache = new ConcurrentHashMap<>();

    public ReferralService(ReferralCodeRepository referralCodeRepository,
                           ReferralRepository referralRepository,
                           ReferralCommissionRepository referralCommissionRepository,
                           ReferralClawbackRepository clawbackRepository,
                           ReferralClickFlusher clickFlusher,
                           UserRepository userRepository,
                           UserSubscriptionRepository subscriptionRepository,
                           PaymentMethodRepository paymentMethodRepository,
                           PaymentHistoryRepository paymentHistoryRepository,
                           ReferralPayoutRepository referralPayoutRepository,
                           ReferralPayoutProfileRepository payoutProfileRepository,
                           TaxIdEncryptor taxIdEncryptor) {
        this.referralCodeRepository = referralCodeRepository;
        this.referralRepository = referralRepository;
        this.referralCommissionRepository = referralCommissionRepository;
        this.clawbackRepository = clawbackRepository;
        this.clickFlusher = clickFlusher;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.referralPayoutRepository = referralPayoutRepository;
        this.payoutProfileRepository = payoutProfileRepository;
        this.taxIdEncryptor = taxIdEncryptor;
    }

    @Transactional
    public ReferralCode getOrCreateReferralCode(Long userId) {
        return referralCodeRepository.findByUserId(userId).orElseGet(() -> {
            ReferralCode rc = new ReferralCode();
            rc.setUserId(userId);
            rc.setCode(generateUniqueCode());
            return referralCodeRepository.save(rc);
        });
    }

    public String referralLink(String code) {
        return referralBaseUrl + "/ref/" + code;
    }

    public Optional<Map<String, Object>> validateCode(String code) {
        return referralCodeRepository.findByCode(code)
                .filter(ReferralCode::isActive)
                .flatMap(rc -> userRepository.findById(rc.getUserId()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", code);
                    m.put("referrerUsername", u.getUsername());
                    return m;
                });
    }

    public void recordClick(String code, String ip, String userAgent) {
        ReferralCode rc = codeCache.computeIfAbsent(code, c ->
                referralCodeRepository.findByCode(c).orElse(null));
        if (rc == null || !rc.isActive()) return;

        LocalDate today = LocalDate.now();
        String dedupeKey = code + "::" + sha256Hex(ip == null ? "" : ip) + "::" + sha256Hex(userAgent == null ? "" : userAgent);
        boolean unique = !today.equals(uniqueSeen.get(dedupeKey));
        if (unique) uniqueSeen.put(dedupeKey, today);

        ClickBucket bucket = clickBuffer.computeIfAbsent(rc.getId() + "::" + today, k -> new ClickBucket());
        bucket.clicks.incrementAndGet();
        if (unique) bucket.uniqueClicks.incrementAndGet();
    }

    @Scheduled(fixedDelayString = "${app.referral.click-flush-ms:60000}")
    public void flushClickBuffer() {
        for (String key : new ArrayList<>(clickBuffer.keySet())) {
            ClickBucket b = clickBuffer.remove(key);
            if (b == null) continue;
            String[] parts = key.split("::", 2);
            Long codeId = Long.valueOf(parts[0]);
            LocalDate day = LocalDate.parse(parts[1]);
            try {
                clickFlusher.upsertDaily(codeId, day, b.clicks.get(), b.uniqueClicks.get());
            } catch (Exception ex) {
                log.warn("Failed to flush referral clicks for codeId={} day={}: {}", codeId, day, ex.getMessage());
                clickBuffer.merge(key, b, (oldB, newB) -> {
                    oldB.clicks.addAndGet(newB.clicks.get());
                    oldB.uniqueClicks.addAndGet(newB.uniqueClicks.get());
                    return oldB;
                });
            }
        }
        uniqueSeen.values().removeIf(d -> d.isBefore(LocalDate.now()));
    }

    @PreDestroy
    public void onShutdown() {
        flushClickBuffer();
    }

    public Map<String, Object> getMyDashboard(Long userId) {
        ReferralCode rc = getOrCreateReferralCode(userId);
        long signups = referralRepository.countByReferrerUserIdAndStatus(userId, STATUS_SIGNED_UP)
                + referralRepository.countByReferrerUserIdAndStatus(userId, STATUS_CONVERTED);
        long conversions = referralRepository.countByReferrerUserIdAndStatus(userId, STATUS_CONVERTED);
        BigDecimal pending = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(userId, COMMISSION_PENDING));
        BigDecimal payable = payableBalance(userId);
        BigDecimal paid = nz(referralPayoutRepository.sumAmountByReferrerAndStatusIn(userId, CONFIRMED_PAYOUT_STATUSES));
        BigDecimal clawedBack = nz(clawbackRepository.sumAmountByReferrer(userId));

        // Payout rail is decided by the referrer's declared country (US -> Connect when enabled,
        // everyone else -> CSV). null = connect-mode but country not yet declared.
        ReferralPayoutProfile profile = payoutProfileRepository.findByUserId(userId).orElse(null);
        String declaredCountry = profile != null ? profile.getCountry() : null;
        String payoutMethodForUser = effectivePayoutMethod(profile);
        boolean connectEnabled = "connect".equalsIgnoreCase(payoutMethod);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", rc.getCode());
        m.put("link", referralLink(rc.getCode()));
        m.put("clicks", rc.getClicksCount());
        m.put("uniqueClicks", rc.getUniqueClicksCount());
        m.put("signups", signups);
        m.put("conversions", conversions);
        m.put("pendingBalance", pending);
        m.put("accruedBalance", payable);
        m.put("paidOut", paid);
        m.put("clawedBack", clawedBack);
        m.put("active", rc.isActive());
        m.put("liveReferred", subscriptionRepository.countLiveReferred(userId));
        m.put("minLiveReferred", minLiveReferred);
        m.put("payoutThreshold", payoutThreshold);
        m.put("connectEnabled", connectEnabled);
        m.put("connectEligible", isConnectCountry(declaredCountry));
        m.put("declaredCountry", declaredCountry);
        // "CONNECT" | "CSV" | null (null only when connectEnabled but no country declared yet).
        m.put("payoutMethod", payoutMethodForUser);
        return m;
    }

    @Transactional
    public void attributeSignup(Long newUserId, String code) {
        if (code == null || code.isBlank()) return;
        Optional<ReferralCode> rcOpt = referralCodeRepository.findByCode(code.trim());
        if (rcOpt.isEmpty() || !rcOpt.get().isActive()) return;
        ReferralCode rc = rcOpt.get();
        if (rc.getUserId().equals(newUserId)) return;
        if (referralRepository.findByReferredUserId(newUserId).isPresent()) return;
        Referral r = new Referral();
        r.setReferralCodeId(rc.getId());
        r.setReferrerUserId(rc.getUserId());
        r.setReferredUserId(newUserId);
        r.setStatus(STATUS_SIGNED_UP);
        r.setSignupAt(LocalDateTime.now());
        r.setFirstYearEnd(LocalDate.now().plusMonths(12));
        referralRepository.save(r);
        log.info("Attributed referral: referredUserId={} referrerUserId={} code={}", newUserId, rc.getUserId(), code);
    }

    @Transactional
    public void accrueCommissionOnPayment(Long userId, Long paymentHistoryId, BigDecimal paymentAmount) {
        // Legacy/manual entry point (SubscriptionService non-Stripe paths). No Stripe invoice id, so
        // the basis is the charge amount and clawback-matching-by-invoice does not apply. No billing
        // cycle is known here, so it accrues as a single installment (monthly-equivalent).
        accrueCommissionOnPayment(userId, paymentHistoryId, null, paymentAmount, null);
    }

    /**
     * Stripe-native accrual entry point (cycle-agnostic overload). Kept for compatibility; new
     * callers should pass the billing cycle so annual invoices amortize.
     */
    @Transactional
    public void accrueCommissionOnPayment(Long userId, Long paymentHistoryId, String stripeInvoiceId, BigDecimal basisAmount) {
        accrueCommissionOnPayment(userId, paymentHistoryId, stripeInvoiceId, basisAmount, null);
    }

    /**
     * Stripe-native accrual entry point. Called from {@code StripeWebhookService} on
     * {@code invoice.paid} with the invoice's subtotal ex-tax post-discount as the basis and the
     * billing cycle. Implements the two-state accrual: creates PENDING accruals that mature to LOCKED
     * after the hold period, gated by the >=2 live referred users qualifier, the self-referral
     * card-fingerprint re-check, and the 12-month earning window anchored to the first paid invoice.
     * Prospective only (no back-pay). An ANNUAL invoice is amortized into {@code referralWindowMonths}
     * (12) monthly installments, each maturing one month apart, so the fee is recognized over the
     * subscription term and clawback exposure is spread; a MONTHLY invoice accrues a single row.
     */
    @Transactional
    public void accrueCommissionOnPayment(Long userId, Long paymentHistoryId, String stripeInvoiceId,
                                          BigDecimal basisAmount, String billingCycle) {
        if (basisAmount == null || basisAmount.signum() <= 0) return;
        Optional<Referral> refOpt = referralRepository.findByReferredUserId(userId);
        if (refOpt.isEmpty()) return;
        Referral ref = refOpt.get();
        if (ref.isVoided()) return;
        // Idempotency: an annual invoice produces up to 12 rows sharing this payment_history_id, so
        // check non-empty rather than a single optional. Webhook retries / sandbox replay skip then.
        if (!referralCommissionRepository.findByPaymentHistoryId(paymentHistoryId).isEmpty()) return;

        // Anchor both clocks to the first paid invoice (not signup). On the first accrual, record it
        // and (re)compute the 12-month earning window from that date.
        if (ref.getFirstPaidInvoiceAt() == null) {
            ref.setFirstPaidInvoiceAt(LocalDateTime.now());
            ref.setFirstYearEnd(ref.getFirstPaidInvoiceAt().toLocalDate().plusMonths(referralWindowMonths));
        }
        // 12-month window: stop accruing after firstYearEnd (but still persist the anchored dates).
        if (ref.getFirstYearEnd() != null && LocalDate.now().isAfter(ref.getFirstYearEnd())) {
            referralRepository.save(ref);
            return;
        }

        // Self-referral fraud re-check at first accrual: the referred user's card fingerprint is not
        // known until they add a payment method. A match against the referrer voids the referral.
        if (!ref.isCardFingerprintChecked()) {
            ref.setCardFingerprintChecked(true);
            if (isSelfReferralByFingerprint(ref.getReferrerUserId(), userId)) {
                ref.setVoided(true);
                referralRepository.save(ref);
                log.warn("Voided self-referral (card fingerprint match): referrerUserId={} referredUserId={}",
                        ref.getReferrerUserId(), userId);
                return;
            }
        }

        // No country gate at accrual: a referrer accrues a balance regardless of country. Country only
        // selects the payout rail (Connect for connect-countries, CSV elsewhere) at payout time.

        // Flip the referral to CONVERTED regardless of whether a fee accrues (the referred user did
        // pay), but only accrue a fee once the >=2 distinct live referred users qualifier is met.
        if (STATUS_SIGNED_UP.equals(ref.getStatus())) {
            ref.setStatus(STATUS_CONVERTED);
            ref.setConvertedAt(LocalDateTime.now());
        }
        long liveReferred = subscriptionRepository.countLiveReferred(ref.getReferrerUserId());
        if (liveReferred < minLiveReferred) {
            referralRepository.save(ref);
            log.info("Skipping referral accrual (qualifier not met): referrerUserId={} liveReferred={} min={}",
                    ref.getReferrerUserId(), liveReferred, minLiveReferred);
            return;
        }

        // Amortize an annual upfront invoice into 12 monthly installments; a monthly invoice is one.
        int installments = "ANNUAL".equalsIgnoreCase(billingCycle) ? Math.max(1, referralWindowMonths) : 1;
        BigDecimal totalCommission = basisAmount.multiply(BigDecimal.valueOf(commissionRate))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal perInstallment = installments > 1
                ? totalCommission.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP)
                : totalCommission;
        LocalDateTime anchor = ref.getFirstPaidInvoiceAt() != null ? ref.getFirstPaidInvoiceAt() : LocalDateTime.now();
        for (int i = 0; i < installments; i++) {
            // Last installment absorbs rounding drift so the installments sum to exactly totalCommission.
            BigDecimal amount = (i == installments - 1)
                    ? totalCommission.subtract(perInstallment.multiply(BigDecimal.valueOf(installments - 1)))
                    : perInstallment;
            BigDecimal basis = basisAmount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
            ReferralCommission rc = new ReferralCommission();
            rc.setReferralId(ref.getId());
            rc.setReferrerUserId(ref.getReferrerUserId());
            rc.setReferredUserId(userId);
            rc.setPaymentHistoryId(paymentHistoryId);
            rc.setBasisAmount(basis);
            rc.setCommissionAmount(amount);
            rc.setStatus(COMMISSION_PENDING); // held; matures to LOCKED via lockMaturedPendingAccruals
            // Installment i matures at anchor + i months + holdDays. Monthly (i=0) = anchor + holdDays.
            rc.setLockEligibleAt(anchor.plusMonths(i).plusDays(holdDays));
            referralCommissionRepository.save(rc);
        }
        referralRepository.save(ref);
        log.info("Accrued referral commission (PENDING, {} installment{}): referrerUserId={} referredUserId={} paymentId={} basis={} totalCommission={} cycle={}",
                installments, installments == 1 ? "" : "s", ref.getReferrerUserId(), userId, paymentHistoryId,
                basisAmount, totalCommission, billingCycle);
    }

    /**
     * Claw back the fee a refunded (or lost-disputed) invoice generated. Matched via the Stripe
     * invoice id on the local PaymentHistory. An annual invoice may have produced up to 12 amortized
     * accrual rows; each is handled per-row and idempotently (one clawback per accrual row, unique on
     * referral_commission_id). For each row: PENDING (within hold / future installment) -> VOID (the
     * fee was never or will never be payable); LOCKED/realized -> a full clawback row. Repeated
     * {@code charge.refunded} deliveries or multiple partial refunds on the same invoice do not
     * double-clawback. A clawback on an already paid-out accrual drives the referrer's balance
     * negative and is recovered from future accruals (ledger-only; no Stripe transfer reversal).
     */
    @Transactional
    public void clawbackCommission(String stripeInvoiceId, String reason) {
        if (stripeInvoiceId == null || stripeInvoiceId.isBlank()) return;
        Optional<PaymentHistory> phOpt = paymentHistoryRepository.findByStripeInvoiceId(stripeInvoiceId);
        if (phOpt.isEmpty()) {
            log.debug("Referral clawback: no payment history for invoiceId={}", stripeInvoiceId);
            return;
        }
        Long paymentHistoryId = phOpt.get().getId();
        List<ReferralCommission> rows = referralCommissionRepository.findByPaymentHistoryId(paymentHistoryId);
        if (rows.isEmpty()) {
            log.debug("Referral clawback: no commission for invoiceId={} paymentHistoryId={}", stripeInvoiceId, paymentHistoryId);
            return;
        }
        int clawed = 0, voided = 0, skipped = 0;
        for (ReferralCommission rc : rows) {
            if (clawbackRepository.existsByReferralCommissionId(rc.getId())) {
                skipped++;
                continue;
            }
            if (COMMISSION_VOID.equals(rc.getStatus())) {
                skipped++;
                continue;
            }
            if (COMMISSION_PENDING.equals(rc.getStatus())) {
                // Refund within the hold, or a future not-yet-recognized installment: never payable.
                rc.setStatus(COMMISSION_VOID);
                referralCommissionRepository.save(rc);
                voided++;
                continue;
            }
            ReferralClawback cb = new ReferralClawback();
            cb.setReferrerUserId(rc.getReferrerUserId());
            cb.setReferredUserId(rc.getReferredUserId());
            cb.setReferralCommissionId(rc.getId());
            cb.setPaymentHistoryId(paymentHistoryId);
            cb.setStripeInvoiceId(stripeInvoiceId);
            cb.setAmount(rc.getCommissionAmount());
            cb.setReason(reason);
            clawbackRepository.save(cb);
            clawed++;
        }
        log.info("Referral clawback processed: invoiceId={} rows={} clawed={} voided={} skipped={} reason={}",
                stripeInvoiceId, rows.size(), clawed, voided, skipped, reason);
    }

    /** Daily job: mature PENDING accruals whose hold period has elapsed into LOCKED (payable). */
    @Scheduled(cron = "${app.referral.lock-cron:0 30 3 * * *}")
    @Transactional
    public void lockMaturedPendingAccruals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(holdDays);
        List<ReferralCommission> pending = referralCommissionRepository.findPendingMatured(now, cutoff);
        for (ReferralCommission rc : pending) {
            rc.setStatus(COMMISSION_LOCKED);
            rc.setLockedAt(now);
            referralCommissionRepository.save(rc);
        }
        log.info("Referral accrual lock run: matured {} PENDING -> LOCKED (now={}, cutoff={})", pending.size(), now, cutoff);
    }

    /** Net payable balance for a referrer = realized accruals - clawbacks - confirmed payouts. */
    private BigDecimal payableBalance(Long referrerId) {
        BigDecimal realized = nz(referralCommissionRepository.sumAmountByReferrerAndStatusIn(referrerId, REALIZED_STATUSES));
        BigDecimal clawedBack = nz(clawbackRepository.sumAmountByReferrer(referrerId));
        BigDecimal paidOut = nz(referralPayoutRepository.sumAmountByReferrerAndStatusIn(referrerId, CONFIRMED_PAYOUT_STATUSES));
        return realized.subtract(clawedBack).subtract(paidOut);
    }

    private boolean isSelfReferralByFingerprint(Long referrerId, Long referredId) {
        PaymentMethod referredPm = paymentMethodRepository.findFirstByUserIdAndIsDefaultTrue(referredId).orElse(null);
        if (referredPm == null || referredPm.getCardFingerprint() == null) return false;
        String fp = referredPm.getCardFingerprint();
        for (PaymentMethod m : paymentMethodRepository.findByCardFingerprint(fp)) {
            if (!m.getUserId().equals(referredId) && m.getUserId().equals(referrerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The payout rail for a referrer, decided by their declared payout-profile country:
     * <ul>
     *   <li>global {@code payout-method=csv} (pre-Connect-approval) -> "CSV" for everyone;</li>
     *   <li>{@code payout-method=connect} -> "CONNECT" for referrers in {@code connect-countries}
     *       (default US), "CSV" for everyone else;</li>
     *   <li>{@code connect} with no country declared -> null (undetermined): skip payout until they
     *       declare a country so the right onboarding is shown.</li>
     * </ul>
     * The referrer must declare their country (in the payout profile) before the method is chosen.
     */
    private String effectivePayoutMethod(ReferralPayoutProfile profile) {
        if (!"connect".equalsIgnoreCase(payoutMethod)) return "CSV";
        String country = profile != null ? profile.getCountry() : null;
        if (country == null || country.isBlank()) return null;
        return connectCountries().contains(country.trim().toUpperCase()) ? "CONNECT" : "CSV";
    }

    private boolean isConnectCountry(String country) {
        if (country == null || country.isBlank()) return false;
        return connectCountries().contains(country.trim().toUpperCase());
    }

    private Set<String> connectCountries() {
        if (connectCountriesCsv == null || connectCountriesCsv.isBlank()) return Set.of();
        return Arrays.stream(connectCountriesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Scheduled(cron = "${app.referral.payout-cron:0 0 0 L * *}")
    @Transactional
    public void runMonthlyPayouts() {
        // Idempotency key = (referrer_id, YYYY-MM) in UTC. Re-running the cron (or the admin trigger)
        // for the same month never double-pays: a prior payout for this run+referrer is skipped.
        String runId = YearMonth.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Long> referrers = referralCommissionRepository.findDistinctReferrersWithRealizedAccruals();
        int payouts = 0, skippedNotReady = 0, skippedBelowThreshold = 0, skippedAlreadyRun = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Long referrerId : referrers) {
            if (referralPayoutRepository.existsByRunIdAndReferrerUserId(runId, referrerId)) {
                skippedAlreadyRun++;
                continue;
            }
            BigDecimal balance = payableBalance(referrerId);
            if (balance.compareTo(BigDecimal.valueOf(payoutThreshold)) < 0) {
                skippedBelowThreshold++;
                continue;
            }
            ReferralPayoutProfile profile = payoutProfileRepository.findByUserId(referrerId).orElse(null);
            // Payout rail is chosen by the referrer's declared country. null = connect-mode with no
            // country declared -> can't pick a rail; skip and leave the balance for next month.
            String method = effectivePayoutMethod(profile);
            if (method == null) {
                skippedNotReady++;
                log.info("Skipping payout: referrerUserId={} balance={} country not declared (connect-mode)", referrerId, balance);
                continue;
            }
            if ("CONNECT".equals(method)) {
                // Stripe Connect Express transfer path. The internal ledger remains source of truth;
                // a real Transfer + payout.paid confirmation would deduct here. Stubbed until platform
                // approval — skip so no payout is recorded for Connect referrers yet.
                skippedNotReady++;
                log.warn("Connect payout path not enabled yet; skipping referrerUserId={} (connect-eligible, awaiting approval)", referrerId);
                continue;
            }
            // CSV path (default / safety valve / non-connect-country referrers): record a COMPLETED
            // payout for the full net balance. The ledger math subtracts confirmed payouts, so accruals
            // stay LOCKED and this row offsets them; linkPayout records the audit association only.
            if (!isPayoutReady(profile)) {
                // Onboard referrers only when they hit the threshold; until then leave the balance for
                // a future run and (TODO) notify them to complete onboarding. Never fail the run.
                skippedNotReady++;
                log.info("Skipping payout: referrerUserId={} balance={} not onboarded (csv)", referrerId, balance);
                continue;
            }
            ReferralPayout payout = new ReferralPayout();
            payout.setReferrerUserId(referrerId);
            payout.setAmount(balance);
            payout.setStatus("COMPLETED");
            payout.setMethod("CSV");
            payout.setRunId(runId);
            payout.setCompletedAt(LocalDateTime.now());
            referralPayoutRepository.save(payout);
            referralCommissionRepository.linkPayout(referrerId, payout.getId());
            payouts++;
            totalPaid = totalPaid.add(balance);
            log.info("Referral payout: referrerUserId={} amount={} method={} payoutId={} runId={}", referrerId, balance, method, payout.getId(), runId);
        }
        log.info("Referral payout run complete: runId={} payouts={} totalPaid={} skippedBelow={} skippedNotReady={} skippedAlready={}",
                runId, payouts, totalPaid, skippedBelowThreshold, skippedNotReady, skippedAlreadyRun);
    }

    public List<Map<String, Object>> getAdminOverview() {
        List<ReferralCode> codes = referralCodeRepository.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReferralCode rc : codes) {
            Long referrerId = rc.getUserId();
            String username = userRepository.findById(referrerId).map(User::getUsername).orElse("(deleted)");
            long signups = referralRepository.countByReferrerUserIdAndStatus(referrerId, STATUS_SIGNED_UP)
                    + referralRepository.countByReferrerUserIdAndStatus(referrerId, STATUS_CONVERTED);
            long conversions = referralRepository.countByReferrerUserIdAndStatus(referrerId, STATUS_CONVERTED);
            BigDecimal pending = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(referrerId, COMMISSION_PENDING));
            BigDecimal payable = payableBalance(referrerId);
            BigDecimal paid = nz(referralPayoutRepository.sumAmountByReferrerAndStatusIn(referrerId, CONFIRMED_PAYOUT_STATUSES));
            BigDecimal clawedBack = nz(clawbackRepository.sumAmountByReferrer(referrerId));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", referrerId);
            m.put("username", username);
            m.put("code", rc.getCode());
            m.put("active", rc.isActive());
            m.put("clicks", rc.getClicksCount());
            m.put("uniqueClicks", rc.getUniqueClicksCount());
            m.put("signups", signups);
            m.put("conversions", conversions);
            m.put("pendingBalance", pending);
            m.put("accruedBalance", payable);
            m.put("paidOut", paid);
            m.put("clawedBack", clawedBack);
            rows.add(m);
        }
        return rows;
    }

    public List<Map<String, Object>> getAllPayoutsAdmin() {
        List<ReferralPayout> payouts = referralPayoutRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReferralPayout p : payouts) {
            String username = userRepository.findById(p.getReferrerUserId()).map(User::getUsername).orElse("(deleted)");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("referrerUserId", p.getReferrerUserId());
            m.put("username", username);
            m.put("amount", p.getAmount());
            m.put("status", p.getStatus());
            m.put("method", p.getMethod());
            m.put("runId", p.getRunId());
            m.put("createdAt", p.getCreatedAt());
            m.put("completedAt", p.getCompletedAt());
            rows.add(m);
        }
        return rows;
    }

    public void evictCodeCache(String code) {
        codeCache.remove(code);
    }

    @Transactional
    public Map<String, Object> getPayoutProfile(Long userId) {
        ReferralPayoutProfile p = payoutProfileRepository.findByUserId(userId).orElseGet(() -> {
            ReferralPayoutProfile np = new ReferralPayoutProfile();
            np.setUserId(userId);
            return np;
        });
        return toProfileDto(p);
    }

    @Transactional
    public Map<String, Object> savePayoutProfile(Long userId, Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("Profile body is required");
        }
        String email = str(body.get("payoutEmail"));
        if (email != null && !EMAIL_RE.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid payout email");
        }
        ReferralPayoutProfile p = payoutProfileRepository.findByUserId(userId).orElseGet(() -> {
            ReferralPayoutProfile np = new ReferralPayoutProfile();
            np.setUserId(userId);
            return np;
        });
        p.setPayableName(str(body.get("payableName")));
        p.setPayoutEmail(email);
        p.setInternational(bool(body.get("international")));
        p.setEntityType(str(body.get("entityType")));
        p.setAddressLine1(str(body.get("addressLine1")));
        p.setAddressLine2(str(body.get("addressLine2")));
        p.setCity(str(body.get("city")));
        p.setState(str(body.get("state")));
        p.setPostalCode(str(body.get("postalCode")));
        p.setCountry(str(body.get("country")));
        String taxId = str(body.get("taxId"));
        if (taxId != null) {
            p.setTaxIdEncrypted(taxIdEncryptor.encrypt(taxId));
            p.setTaxIdLast4(TaxIdEncryptor.last4(taxId));
        }
        payoutProfileRepository.save(p);
        log.info("Saved referral payout profile: userId={} readyForPayout={} taxInfoComplete={}",
                userId, isPayoutReady(p), taxInfoComplete(p));
        return toProfileDto(p);
    }

    public String generatePayoutCsv(String runId) {
        if (runId == null || runId.isBlank()) return "";
        List<ReferralPayout> payouts = referralPayoutRepository.findByRunIdOrderByReferrerUserIdAsc(runId);
        StringBuilder sb = new StringBuilder();
        sb.append("Name,Payment,Payment eMail,Memo,Run Id\n");
        for (ReferralPayout p : payouts) {
            ReferralPayoutProfile prof = payoutProfileRepository.findByUserId(p.getReferrerUserId()).orElse(null);
            String name = prof != null ? prof.getPayableName() : "";
            String email = prof != null ? prof.getPayoutEmail() : "";
            sb.append(csv(name)).append(",")
                    .append(csv(p.getAmount().toPlainString())).append(",")
                    .append(csv(email)).append(",")
                    .append(csv("SpringHi.ai referral payment")).append(",")
                    .append(csv(runId)).append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> toProfileDto(ReferralPayoutProfile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("payableName", p.getPayableName());
        m.put("payoutEmail", p.getPayoutEmail());
        m.put("international", p.isInternational());
        m.put("entityType", p.getEntityType());
        m.put("taxIdLast4", p.getTaxIdLast4());
        m.put("hasTaxId", p.getTaxIdEncrypted() != null && !p.getTaxIdEncrypted().isBlank());
        m.put("addressLine1", p.getAddressLine1());
        m.put("addressLine2", p.getAddressLine2());
        m.put("city", p.getCity());
        m.put("state", p.getState());
        m.put("postalCode", p.getPostalCode());
        m.put("country", p.getCountry());
        m.put("readyForPayout", isPayoutReady(p));
        m.put("taxInfoComplete", taxInfoComplete(p));
        return m;
    }

    private boolean isPayoutReady(ReferralPayoutProfile p) {
        if (p == null) return false;
        return notBlank(p.getPayableName()) && notBlank(p.getPayoutEmail());
    }

    private boolean taxInfoComplete(ReferralPayoutProfile p) {
        if (p == null) return false;
        if (p.isInternational()) return true;
        boolean hasTaxId = p.getTaxIdEncrypted() != null && !p.getTaxIdEncrypted().isBlank();
        if ("CORPORATION".equalsIgnoreCase(p.getEntityType())) {
            return hasTaxId;
        }
        boolean addressOk = notBlank(p.getAddressLine1()) && notBlank(p.getCity()) && notBlank(p.getState())
                && notBlank(p.getPostalCode()) && notBlank(p.getCountry());
        return hasTaxId && addressOk;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private boolean bool(Object o) {
        return o != null && Boolean.parseBoolean(String.valueOf(o));
    }

    private String csv(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!referralCodeRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Failed to generate unique referral code");
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class ClickBucket {
        final AtomicInteger clicks = new AtomicInteger(0);
        final AtomicInteger uniqueClicks = new AtomicInteger(0);
    }
}
