package com.springhi.user.service;

import com.springhi.user.model.Ad;
import com.springhi.user.model.AdStat;
import com.springhi.user.model.AdSignup;
import com.springhi.user.repository.AdRepository;
import com.springhi.user.repository.AdStatRepository;
import com.springhi.user.repository.AdSignupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class AdService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 8;
    private static final Set<String> VALID_PLATFORMS = Set.of(
            "TIKTOK", "GOOGLE", "META", "YOUTUBE", "LINKEDIN", "X", "EMAIL", "OTHER");
    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "PAUSED", "ARCHIVED");

    private static final Logger log = LoggerFactory.getLogger(AdService.class);

    private final AdRepository adRepository;
    private final AdStatRepository adStatRepository;
    private final AdSignupRepository adSignupRepository;
    private final SecureRandom random = new SecureRandom();

    public AdService(AdRepository adRepository,
                     AdStatRepository adStatRepository,
                     AdSignupRepository adSignupRepository) {
        this.adRepository = adRepository;
        this.adStatRepository = adStatRepository;
        this.adSignupRepository = adSignupRepository;
    }

    @Transactional
    public Map<String, Object> createAd(Long ownerUserId, Map<String, Object> body) {
        Ad ad = new Ad();
        ad.setOwnerUserId(ownerUserId);
        ad.setName(requireName(body.get("name")));
        ad.setPlatform(requirePlatform(body.get("platform")));
        ad.setExternalRef(str(body.get("externalRef")));
        ad.setNotes(str(body.get("notes")));
        String status = str(body.get("status"));
        ad.setStatus((status == null || status.isBlank()) ? "ACTIVE" : requireStatus(status));
        ad.setTrackingCode(generateUniqueTrackingCode());
        return toDto(adRepository.save(ad));
    }

    @Transactional
    public Map<String, Object> updateAd(Long id, Map<String, Object> body) {
        Ad ad = adRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ad not found"));
        if (body.get("name") != null) ad.setName(requireName(body.get("name")));
        if (body.get("platform") != null) ad.setPlatform(requirePlatform(body.get("platform")));
        ad.setExternalRef(str(body.get("externalRef")));
        ad.setNotes(str(body.get("notes")));
        String status = str(body.get("status"));
        if (status != null && !status.isBlank()) ad.setStatus(requireStatus(status));
        return toDto(adRepository.save(ad));
    }

    @Transactional
    public void deleteAd(Long id) {
        if (!adRepository.existsById(id)) throw new IllegalArgumentException("Ad not found");
        adSignupRepository.deleteByAdId(id);
        adSignupRepository.flush();
        adStatRepository.deleteByAdId(id);
        adStatRepository.flush();
        adRepository.deleteById(id);
    }

    // Layer 2: attribute a new signup to the ad whose tracking code matches ?ad=CODE. Idempotent:
    // a no-op when the code is blank/unknown or the user was already attributed to an ad.
    @Transactional
    public void attributeSignup(Long userId, String code) {
        if (code == null || code.isBlank()) return;
        Optional<Ad> adOpt = adRepository.findByTrackingCode(code.trim());
        if (adOpt.isEmpty()) return;
        if (adSignupRepository.existsByUserId(userId)) return;
        AdSignup a = new AdSignup();
        a.setAdId(adOpt.get().getId());
        a.setUserId(userId);
        adSignupRepository.save(a);
        log.info("Attributed ad signup: userId={} adId={} code={}", userId, adOpt.get().getId(), code);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdSummary() {
        return adRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    // Comparison board: every ad with its aggregated metrics + derived KPIs over [from, to].
    @Transactional(readOnly = true)
    public List<Map<String, Object>> comparison(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        List<Ad> ads = adRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, Object[]> aggById = new HashMap<>();
        for (Object[] row : adStatRepository.sumByAdAndDateRange(range[0], range[1])) {
            aggById.put(((Number) row[0]).longValue(), row);
        }
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].atTime(LocalTime.MAX);
        Map<Long, Long> signupsByAd = groupLong(adSignupRepository.countSignupsByAd(start, end));
        Map<Long, Long> paidByAd = groupLong(adSignupRepository.countPaidByAd(start, end));
        Map<Long, BigDecimal> revenueByAd = groupBd(adSignupRepository.sumRevenueByAd(start, end));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Ad ad : ads) {
            Object[] row = aggById.get(ad.getId());
            BigDecimal spend = (row != null) ? bd(row[3]) : BigDecimal.ZERO;
            Map<String, Object> entry = toDto(ad);
            entry.putAll(metricsAndKpis(row, 1));
            entry.putAll(attributedKpis(signupsByAd.get(ad.getId()), paidByAd.get(ad.getId()),
                    revenueByAd.get(ad.getId()), spend));
            out.add(entry);
        }
        return out;
    }

    // One ad: aggregated metrics + KPIs over [from, to], plus the daily breakdown rows in range.
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id, LocalDate from, LocalDate to) {
        Ad ad = adRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ad not found"));
        LocalDate[] range = normalizeRange(from, to);
        List<Object[]> aggRows = adStatRepository.sumByAdIdAndDateRange(id, range[0], range[1]);
        Object[] row = aggRows.isEmpty() ? null : aggRows.get(0);
        BigDecimal spend = (row != null) ? bd(row[2]) : BigDecimal.ZERO;
        Map<String, Object> out = toDto(ad);
        out.put("rangeFrom", range[0].toString());
        out.put("rangeTo", range[1].toString());
        out.putAll(metricsAndKpis(row, 0));
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].atTime(LocalTime.MAX);
        long signups = adSignupRepository.countByAdIdAndSignupAtBetween(id, start, end);
        long paid = adSignupRepository.countPaidForAd(id, start, end);
        BigDecimal autoRevenue = adSignupRepository.sumRevenueForAd(id, start, end);
        if (autoRevenue == null) autoRevenue = BigDecimal.ZERO;
        out.putAll(attributedKpis(signups, paid, autoRevenue, spend));
        List<Map<String, Object>> daily = new ArrayList<>();
        for (AdStat s : adStatRepository.findByAdIdOrderByStatDateAsc(id)) {
            if (s.getStatDate().isBefore(range[0]) || s.getStatDate().isAfter(range[1])) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("statDate", s.getStatDate().toString());
            d.put("impressions", s.getImpressions());
            d.put("clicks", s.getClicks());
            d.put("spend", s.getSpend());
            d.put("conversions", s.getConversions());
            d.put("revenue", s.getRevenue());
            d.putAll(derivedKpis(s.getImpressions(), s.getClicks(), s.getSpend(), s.getConversions(), s.getRevenue()));
            daily.add(d);
        }
        out.put("daily", daily);
        return out;
    }

    @Transactional
    public Map<String, Object> upsertStat(Long adId, Map<String, Object> body) {
        if (!adRepository.existsById(adId)) throw new IllegalArgumentException("Ad not found");
        LocalDate date = requireDate(body.get("statDate"));
        long impressions = nn(body.get("impressions")).longValue();
        long clicks = nn(body.get("clicks")).longValue();
        BigDecimal spend = bd(body.get("spend"));
        int conversions = nn(body.get("conversions")).intValue();
        BigDecimal revenue = bd(body.get("revenue"));

        AdStat stat = adStatRepository.findByAdIdAndStatDate(adId, date).orElseGet(AdStat::new);
        stat.setAdId(adId);
        stat.setStatDate(date);
        stat.setImpressions(impressions);
        stat.setClicks(clicks);
        stat.setSpend(spend);
        stat.setConversions(conversions);
        stat.setRevenue(revenue);
        return statToDto(adStatRepository.save(stat));
    }

    @Transactional
    public void deleteStat(Long adId, LocalDate date) {
        adStatRepository.findByAdIdAndStatDate(adId, date)
                .ifPresent(s -> adStatRepository.deleteById(s.getId()));
    }

    // ---- helpers ----

    private String generateUniqueTrackingCode() {
        for (int attempt = 0; attempt < 12; attempt++) {
            StringBuilder sb = new StringBuilder("AD");
            for (int i = 0; i < CODE_LEN; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            String code = sb.toString();
            if (!adRepository.existsByTrackingCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique ad tracking code");
    }

    // Coerce an aggregation row into metrics + derived KPIs. offset = index of the first metric
    // column (0 for the single-ad query, 1 for the per-ad query whose first column is adId).
    private Map<String, Object> metricsAndKpis(Object[] row, int offset) {
        long impressions = 0, clicks = 0, conversions = 0;
        BigDecimal spend = BigDecimal.ZERO, revenue = BigDecimal.ZERO;
        if (row != null) {
            impressions = num(row, offset).longValue();
            clicks = num(row, offset + 1).longValue();
            spend = bd(row[offset + 2]);
            conversions = num(row, offset + 3).longValue();
            revenue = bd(row[offset + 4]);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("impressions", impressions);
        m.put("clicks", clicks);
        m.put("spend", spend);
        m.put("conversions", conversions);
        m.put("revenue", revenue);
        m.putAll(derivedKpis(impressions, clicks, spend, conversions, revenue));
        return m;
    }

    private Map<String, Object> derivedKpis(long impressions, long clicks, BigDecimal spend,
                                            long conversions, BigDecimal revenue) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("ctr", impressions > 0 ? round4((double) clicks / impressions) : 0.0);
        k.put("cpc", clicks > 0 ? spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        k.put("cpa", conversions > 0 ? spend.divide(BigDecimal.valueOf(conversions), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        k.put("roas", spendSignum(spend) > 0 ? round4(revenue.divide(spend, 4, RoundingMode.HALF_UP).doubleValue()) : 0.0);
        k.put("roi", spendSignum(spend) > 0 ? round4(revenue.subtract(spend).divide(spend, 4, RoundingMode.HALF_UP).doubleValue()) : 0.0);
        return k;
    }

    // Layer 2 auto-attributed metrics: signups/paid counted from ad_signups, revenue from
    // payment_history, and an attributed ROAS = autoRevenue / manualSpend over the same window.
    private Map<String, Object> attributedKpis(Long signups, Long paid, BigDecimal revenue, BigDecimal spend) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoSignups", signups == null ? 0L : signups);
        m.put("autoPaid", paid == null ? 0L : paid);
        BigDecimal rev = revenue == null ? BigDecimal.ZERO : revenue;
        m.put("autoRevenue", rev);
        m.put("autoRoas", spendSignum(spend) > 0 ? round4(rev.divide(spend, 4, RoundingMode.HALF_UP).doubleValue()) : 0.0);
        return m;
    }

    private Map<Long, Long> groupLong(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return m;
    }

    private Map<Long, BigDecimal> groupBd(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put(((Number) r[0]).longValue(), bd(r[1]));
        return m;
    }

    private Map<String, Object> toDto(Ad ad) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ad.getId());
        m.put("ownerUserId", ad.getOwnerUserId());
        m.put("name", ad.getName());
        m.put("platform", ad.getPlatform());
        m.put("externalRef", ad.getExternalRef());
        m.put("trackingCode", ad.getTrackingCode());
        m.put("status", ad.getStatus());
        m.put("notes", ad.getNotes());
        m.put("createdAt", ad.getCreatedAt() != null ? ad.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> statToDto(AdStat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("adId", s.getAdId());
        m.put("statDate", s.getStatDate().toString());
        m.put("impressions", s.getImpressions());
        m.put("clicks", s.getClicks());
        m.put("spend", s.getSpend());
        m.put("conversions", s.getConversions());
        m.put("revenue", s.getRevenue());
        return m;
    }

    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            to = LocalDate.now();
            from = to.minusDays(29);
        } else if (from == null) {
            from = to.minusDays(29);
        } else if (to == null) {
            to = LocalDate.now();
        }
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        return new LocalDate[]{from, to};
    }

    private String requireName(Object v) {
        String s = str(v);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("name is required");
        if (s.length() > 120) throw new IllegalArgumentException("name must be 120 characters or fewer");
        return s.trim();
    }

    private String requirePlatform(Object v) {
        String s = str(v);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("platform is required");
        String up = s.trim().toUpperCase();
        if (!VALID_PLATFORMS.contains(up)) throw new IllegalArgumentException("Unsupported platform: " + s);
        return up;
    }

    private String requireStatus(Object v) {
        String up = str(v).trim().toUpperCase();
        if (!VALID_STATUSES.contains(up)) throw new IllegalArgumentException("Invalid status: " + v);
        return up;
    }

    private LocalDate requireDate(Object v) {
        String s = str(v);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("statDate is required (yyyy-MM-dd)");
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("statDate must be yyyy-MM-dd");
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Number num(Object[] row, int i) {
        return row[i] == null ? 0L : (Number) row[i];
    }

    private static Number nn(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n;
        return new BigDecimal(v.toString().trim());
    }

    private static BigDecimal bd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static int spendSignum(BigDecimal spend) {
        return spend == null ? 0 : spend.signum();
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
