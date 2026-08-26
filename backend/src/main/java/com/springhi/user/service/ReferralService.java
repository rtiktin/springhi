package com.springhi.user.service;

import com.springhi.user.model.Referral;
import com.springhi.user.model.ReferralCode;
import com.springhi.user.model.ReferralCommission;
import com.springhi.user.model.ReferralPayout;
import com.springhi.user.model.ReferralPayoutProfile;
import com.springhi.user.model.User;
import com.springhi.user.repository.ReferralCodeRepository;
import com.springhi.user.repository.ReferralCommissionRepository;
import com.springhi.user.repository.ReferralPayoutProfileRepository;
import com.springhi.user.repository.ReferralPayoutRepository;
import com.springhi.user.repository.ReferralRepository;
import com.springhi.user.repository.UserRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final String STATUS_SIGNED_UP = "SIGNED_UP";
    private static final String STATUS_CONVERTED = "CONVERTED";
    private static final String COMMISSION_ACCRUED = "ACCRUED";
    private static final String COMMISSION_PAID = "PAID";
    private static final java.util.regex.Pattern EMAIL_RE =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final ReferralCommissionRepository referralCommissionRepository;
    private final ReferralClickFlusher clickFlusher;
    private final UserRepository userRepository;
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

    private final ConcurrentHashMap<String, ClickBucket> clickBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDate> uniqueSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReferralCode> codeCache = new ConcurrentHashMap<>();

    public ReferralService(ReferralCodeRepository referralCodeRepository,
                           ReferralRepository referralRepository,
                           ReferralCommissionRepository referralCommissionRepository,
                           ReferralClickFlusher clickFlusher,
                           UserRepository userRepository,
                           ReferralPayoutRepository referralPayoutRepository,
                           ReferralPayoutProfileRepository payoutProfileRepository,
                           TaxIdEncryptor taxIdEncryptor) {
        this.referralCodeRepository = referralCodeRepository;
        this.referralRepository = referralRepository;
        this.referralCommissionRepository = referralCommissionRepository;
        this.clickFlusher = clickFlusher;
        this.userRepository = userRepository;
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
        BigDecimal accrued = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(userId, COMMISSION_ACCRUED));
        BigDecimal paid = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(userId, COMMISSION_PAID));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", rc.getCode());
        m.put("link", referralLink(rc.getCode()));
        m.put("clicks", rc.getClicksCount());
        m.put("uniqueClicks", rc.getUniqueClicksCount());
        m.put("signups", signups);
        m.put("conversions", conversions);
        m.put("accruedBalance", accrued);
        m.put("paidOut", paid);
        m.put("active", rc.isActive());
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
        if (paymentAmount == null || paymentAmount.signum() <= 0) return;
        Optional<Referral> refOpt = referralRepository.findByReferredUserId(userId);
        if (refOpt.isEmpty()) return;
        Referral ref = refOpt.get();
        if (ref.getFirstYearEnd() != null && LocalDate.now().isAfter(ref.getFirstYearEnd())) return;
        if (referralCommissionRepository.findByPaymentHistoryId(paymentHistoryId).isPresent()) return;
        BigDecimal commission = paymentAmount.multiply(BigDecimal.valueOf(commissionRate))
                .setScale(2, RoundingMode.HALF_UP);
        ReferralCommission rc = new ReferralCommission();
        rc.setReferralId(ref.getId());
        rc.setReferrerUserId(ref.getReferrerUserId());
        rc.setReferredUserId(userId);
        rc.setPaymentHistoryId(paymentHistoryId);
        rc.setBasisAmount(paymentAmount);
        rc.setCommissionAmount(commission);
        rc.setStatus(COMMISSION_ACCRUED);
        referralCommissionRepository.save(rc);
        if (STATUS_SIGNED_UP.equals(ref.getStatus())) {
            ref.setStatus(STATUS_CONVERTED);
            ref.setConvertedAt(LocalDateTime.now());
            referralRepository.save(ref);
        }
        log.info("Accrued referral commission: referrerUserId={} referredUserId={} paymentId={} basis={} commission={}",
                ref.getReferrerUserId(), userId, paymentHistoryId, paymentAmount, commission);
    }

    @Scheduled(cron = "${app.referral.payout-cron:0 0 0 1 * *}")
    @Transactional
    public void runMonthlyPayouts() {
        String runId = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Long> referrers = referralCommissionRepository.findDistinctReferrerUserIdsByStatus(COMMISSION_ACCRUED);
        int payouts = 0;
        int skippedNotReady = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Long referrerId : referrers) {
            BigDecimal accrued = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(referrerId, COMMISSION_ACCRUED));
            if (accrued.compareTo(BigDecimal.valueOf(payoutThreshold)) <= 0) {
                continue;
            }
            ReferralPayoutProfile profile = payoutProfileRepository.findByUserId(referrerId).orElse(null);
            if (!isPayoutReady(profile)) {
                skippedNotReady++;
                log.info("Skipping payout: referrerUserId={} accrued={} payout profile not ready", referrerId, accrued);
                continue;
            }
            ReferralPayout payout = new ReferralPayout();
            payout.setReferrerUserId(referrerId);
            payout.setAmount(accrued);
            payout.setStatus("COMPLETED");
            payout.setMethod("CSV");
            payout.setRunId(runId);
            payout.setCompletedAt(LocalDateTime.now());
            referralPayoutRepository.save(payout);
            referralCommissionRepository.markPaid(referrerId, payout.getId(), COMMISSION_ACCRUED, COMMISSION_PAID);
            payouts++;
            totalPaid = totalPaid.add(accrued);
            log.info("Referral payout: referrerUserId={} amount={} payoutId={} runId={}", referrerId, accrued, payout.getId(), runId);
        }
        log.info("Referral payout run complete: runId={} payouts={} skippedNotReady={} totalPaid={}", runId, payouts, skippedNotReady, totalPaid);
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
            BigDecimal accrued = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(referrerId, COMMISSION_ACCRUED));
            BigDecimal paid = nz(referralCommissionRepository.sumAmountByReferrerAndStatus(referrerId, COMMISSION_PAID));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", referrerId);
            m.put("username", username);
            m.put("code", rc.getCode());
            m.put("active", rc.isActive());
            m.put("clicks", rc.getClicksCount());
            m.put("uniqueClicks", rc.getUniqueClicksCount());
            m.put("signups", signups);
            m.put("conversions", conversions);
            m.put("accruedBalance", accrued);
            m.put("paidOut", paid);
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
