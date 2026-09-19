package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.PortfolioProfileDto;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.dto.SecurityRecommendation;
import com.springhi.portfolio.model.PortfolioRecommendation;
import com.springhi.portfolio.repository.PortfolioRecommendationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PortfolioOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizationService.class);

    private final GeminiService geminiService;
    private final ClaudeService claudeService;
    private final ChatGptService chatGptService;
    private final GrokService grokService;
    private final PortfolioService portfolioService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortfolioOptimizationService(GeminiService geminiService,
                                        ClaudeService claudeService,
                                        ChatGptService chatGptService,
                                        GrokService grokService,
                                        PortfolioService portfolioService,
                                        PortfolioRecommendationRepository recommendationRepository) {
        this.geminiService = geminiService;
        this.claudeService = claudeService;
        this.chatGptService = chatGptService;
        this.grokService = grokService;
        this.portfolioService = portfolioService;
        this.recommendationRepository = recommendationRepository;
    }

    public List<RecommendationDto> getPendingRecommendations(Long portfolioId) {
        List<PortfolioRecommendation> pending = recommendationRepository
                .findByPortfolioIdAndStatusOrderByActionDescIdAsc(portfolioId, "PENDING");
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime latest = pending.stream()
                .map(PortfolioRecommendation::getGeneratedAt)
                .filter(d -> d != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (latest == null) {
            return pending.stream().map(RecommendationDto::from).collect(Collectors.toList());
        }
        return pending.stream()
                .filter(r -> latest.equals(r.getGeneratedAt()))
                .map(RecommendationDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public OptimizationResponse optimize(Long userId, Long portfolioId, String provider, boolean replacePending) {
        PortfolioProfileDto portfolioProfile = portfolioService.getOrCreatePortfolioProfile(portfolioId, userId);
        List<AssetWithPrice> holdings = portfolioService.getUserAssetsWithPrices(portfolioId);
        BigDecimal cashBalance = portfolioService.getCashBalance(portfolioId);

        BigDecimal portfolioMarketValue = holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String prompt = buildPrompt(portfolioProfile, holdings, cashBalance, portfolioMarketValue);
        log.info("Sending rebalancing prompt to {} for portfolioId={}, holdings={}, cash={}",
                provider, portfolioId, holdings.size(), cashBalance);

        try {
            String rawText;
            if ("claude".equalsIgnoreCase(provider)) {
                rawText = claudeService.generateContent(prompt);
            } else if ("chatgpt".equalsIgnoreCase(provider)) {
                rawText = chatGptService.generateContent(prompt);
            } else if ("grok".equalsIgnoreCase(provider)) {
                rawText = grokService.generateContent(prompt);
            } else {
                rawText = geminiService.generateContent(prompt);
            }
            String json = extractJson(rawText, provider);
            List<SecurityRecommendation> recs = objectMapper.readValue(json,
                    new TypeReference<List<SecurityRecommendation>>() {});

            recs = normalizeBuyWeights(recs);
            Integer confidence = extractConfidence(rawText);

            List<RecommendationDto> saved = persistRecommendations(userId, portfolioId, recs, holdings, cashBalance, portfolioMarketValue, portfolioProfile, provider, confidence, replacePending);
            return new OptimizationResponse(saved, null);
        } catch (Exception e) {
            log.error("Optimization failed for portfolioId={}: {}", portfolioId, e.getMessage(), e);
            return new OptimizationResponse(Collections.emptyList(), e.getMessage());
        }
    }

    private List<RecommendationDto> persistRecommendations(Long userId,
                                                            Long portfolioId,
                                                            List<SecurityRecommendation> recs,
                                                            List<AssetWithPrice> holdings,
                                                            BigDecimal cashBalance,
                                                            BigDecimal portfolioMarketValue,
                                                            PortfolioProfileDto profile,
                                                            String provider,
                                                            Integer confidence,
                                                            boolean replacePending) {
        if (replacePending) {
            recommendationRepository.deletePendingForPortfolio(portfolioId);
        }

        Map<String, AssetWithPrice> holdingMap = holdings.stream()
                .collect(Collectors.toMap(AssetWithPrice::getSymbol, h -> h));

        BigDecimal totalValue = portfolioMarketValue.add(cashBalance);

        LocalDateTime now = LocalDateTime.now();

        List<PortfolioRecommendation> entities = recs.stream().map(rec -> {
            PortfolioRecommendation entity = new PortfolioRecommendation();
            entity.setUserId(userId);
            entity.setPortfolioId(portfolioId);
            entity.setGeneratedAt(now);
            entity.setTicker(rec.t());
            entity.setName(rec.n());
            entity.setSector(rec.s());
            entity.setAction(rec.action() != null ? rec.action() : "BUY");
            entity.setWeight(BigDecimal.valueOf(rec.w()).setScale(4, RoundingMode.HALF_UP));
            entity.setRationale(rec.r());
            entity.setStatus("PENDING");
            entity.setAiProvider(provider);
            entity.setConfidenceScore(confidence);
            if (profile != null) {
                entity.setSnapshotRiskLevel(profile.riskLevel());
                entity.setSnapshotGoal(profile.goal());
                entity.setSnapshotHorizonYears(profile.horizonYears());
                entity.setSnapshotLiquidityNeeds(profile.liquidityNeeds());
                entity.setSnapshotAdditionalComments(profile.additionalComments());
                entity.setSnapshotCurrency(profile.currency());
                entity.setSnapshotSectorConstraints(
                        profile.sectorConstraints() != null ? String.join(",", profile.sectorConstraints()) : null);
            }

            if ("SELL".equals(entity.getAction())) {
                AssetWithPrice holding = holdingMap.get(rec.t());
                BigDecimal mv = holding != null && holding.getMarketValue() != null
                        ? holding.getMarketValue() : BigDecimal.ZERO;
                entity.setEstimatedValue(mv);
                if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal sellWeight = mv.divide(totalValue, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                    entity.setWeight(sellWeight);
                }
            } else {
                BigDecimal estValue = cashBalance.multiply(BigDecimal.valueOf(rec.w()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                entity.setEstimatedValue(estValue);
            }

            return entity;
        }).collect(Collectors.toList());

        return recommendationRepository.saveAll(entities).stream()
                .map(RecommendationDto::from)
                .collect(Collectors.toList());
    }

    private String buildPrompt(PortfolioProfileDto profile,
                               List<AssetWithPrice> holdings,
                               BigDecimal cashBalance, BigDecimal portfolioMarketValue) {
        StringBuilder sb = new StringBuilder();

        sb.append("Task: Produce a portfolio rebalancing plan. Return SELL recommendations for positions to exit or trim, and BUY recommendations for new or underweight positions. BUYs are funded ONLY from Available Cash plus the proceeds of your SELL recommendations.\n");
        sb.append("Output: Minified JSON array only. No prose. No markdown. No code blocks.\n");
        sb.append("Keys: t (ticker), n (full name), s (sector), action (\"BUY\" or \"SELL\"), w (weight as number, see rules below), r (rationale max 8 words).\n");
        sb.append("Weight rules: For BUY entries, w = % of the deployable budget (Available Cash + estimated SELL proceeds) to allocate; when BUYs are present their weights must sum to 100. For SELL entries, w = 0. If you recommend no BUYs, return only SELLs (or an empty array) — do not pad with zero-weight BUYs.\n");
        sb.append("After the JSON array on a new line output exactly: confidence=N where N is your overall confidence (0-100) that this plan fits the client profile and market conditions.\n\n");

        sb.append("Client Profile:\n");
        boolean hasNotes = profile != null && profile.additionalComments() != null && !profile.additionalComments().isBlank();

        if (profile != null) {
            if (profile.riskLevel() != null) sb.append("Risk Tolerance: ").append(profile.riskLevel()).append("\n");
            else if (!hasNotes) sb.append("Risk Tolerance: Moderate\n");

            if (profile.goal() != null) sb.append("Primary Objective: ").append(profile.goal()).append("\n");
            else if (!hasNotes) sb.append("Primary Objective: Growth\n");

            if (profile.horizonYears() != null) sb.append("Time Horizon: ").append(profile.horizonYears()).append(" years\n");
            else if (!hasNotes) sb.append("Time Horizon: 10+ years\n");

            if (profile.liquidityNeeds() != null) sb.append("Liquidity Needs: ").append(profile.liquidityNeeds()).append("\n");
            else if (!hasNotes) sb.append("Liquidity Needs: Low\n");

            if (hasNotes) sb.append("Additional Notes: ").append(profile.additionalComments()).append("\n");

            if (profile.sectorConstraints() != null && !profile.sectorConstraints().isEmpty()) {
                sb.append("Preferred Sectors: ").append(String.join(", ", profile.sectorConstraints())).append("\n");
            }
        } else {
            sb.append("Risk Tolerance: Moderate\nPrimary Objective: Growth\nTime Horizon: 10+ years\n");
        }

        boolean taxMode = profile != null && profile.taxOptimization();
        if (taxMode) {
            sb.append("Tax Optimization: ENABLED — minimize taxable events where possible.\n");
            sb.append("  - Prefer holding positions held >= 365 days (long-term capital gains) over those held < 365 days.\n");
            sb.append("  - Prefer selling positions with unrealized losses (tax-loss harvesting) before selling gainers.\n");
            sb.append("  - Avoid selling short-term winners unless the profile goal strongly requires it.\n");
        }

        sb.append("\nCurrent Portfolio:\n");
        sb.append("Available Cash: $").append(cashBalance.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");
        sb.append("Total Holdings Market Value: $").append(portfolioMarketValue.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");

        if (holdings.isEmpty()) {
            sb.append("No existing holdings. This is a fresh portfolio.\n");
        } else {
            if (taxMode) {
                sb.append("Holdings (symbol | quantity | avg cost | current price | market value | unrealized gain/loss | holding days | tax status):\n");
                for (AssetWithPrice h : holdings) {
                    BigDecimal currentPrice = h.getCurrentPrice() != null ? h.getCurrentPrice() : h.getAveragePrice();
                    BigDecimal marketValue = h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO;
                    BigDecimal gainLoss = h.getGainLoss() != null ? h.getGainLoss() : BigDecimal.ZERO;
                    long days = h.getHoldingDays() != null ? h.getHoldingDays() : 0;
                    String taxStatus = days >= 365 ? "LONG_TERM" : "SHORT_TERM";
                    sb.append(String.format("  %s | qty=%.4f | avgCost=$%.4f | price=$%.4f | value=$%.2f | gl=$%.2f | days=%d | %s\n",
                            h.getSymbol(),
                            h.getQuantity(),
                            h.getAveragePrice(),
                            currentPrice,
                            marketValue,
                            gainLoss,
                            days,
                            taxStatus));
                }
            } else {
                sb.append("Holdings (symbol | quantity | avg cost | current price | market value):\n");
                for (AssetWithPrice h : holdings) {
                    BigDecimal currentPrice = h.getCurrentPrice() != null ? h.getCurrentPrice() : h.getAveragePrice();
                    BigDecimal marketValue = h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO;
                    sb.append(String.format("  %s | qty=%.4f | avgCost=$%.4f | price=$%.4f | value=$%.2f\n",
                            h.getSymbol(),
                            h.getQuantity(),
                            h.getAveragePrice(),
                            currentPrice,
                            marketValue));
                }
            }
        }

        sb.append("\nInstructions:\n");
        sb.append("1. Recommend SELL for any holdings that no longer fit the client's goals or are overweight.\n");
        sb.append("2. Recommend BUY for securities that should be added or increased to meet the client's goals.\n");
        sb.append("3. Do not recommend BUY for securities already held unless they are significantly underweight.\n");
        sb.append("4. Funding constraint: total BUY deployment is capped at Available Cash + proceeds from your SELL recommendations. If your BUYs need more capital than the cash on hand, you MUST recommend SELLs to fund them. Never recommend BUYs that cannot be funded.\n");
        sb.append("5. If Available Cash is small and you choose not to sell, recommend NO BUYs (return only SELLs or an empty array) instead of spreading a tiny cash balance across many BUYs.\n");
        sb.append("6. If the portfolio already matches the client profile well, recommend few or no changes — return an empty array or only minor SELLs/BUYs. Do not force unnecessary trades.\n");
        sb.append("7. When changes are warranted, aim for 8-15 total recommendations (combined SELL + BUY). Diversify across sectors. BUY weights represent % of the deployable budget (Available Cash + expected sell proceeds) and must sum to exactly 100.\n");
        sb.append("8. Portfolio concentration: the portfolio should hold no more than 25 distinct securities at one time unless the client's Additional Notes explicitly request otherwise. If current holdings plus your BUYs would exceed 25, recommend SELLs to reduce the count or recommend fewer BUYs.\n");
        sb.append("9. Minimum trade size: do not recommend a BUY for a single ticker whose total deployment would be less than $50, unless the total portfolio value (Available Cash + Total Holdings Market Value) is under $1000. Prefer fewer, larger BUYs over many tiny ones.\n");

        return sb.toString();
    }

    private List<SecurityRecommendation> normalizeBuyWeights(List<SecurityRecommendation> recs) {
        double buyTotal = recs.stream()
                .filter(r -> !"SELL".equalsIgnoreCase(r.action()))
                .mapToDouble(SecurityRecommendation::w)
                .sum();
        if (buyTotal <= 0 || Math.abs(buyTotal - 100.0) < 0.01) {
            return recs;
        }
        log.warn("AI BUY weights sum to {}%, normalizing to 100%", String.format("%.2f", buyTotal));
        double factor = 100.0 / buyTotal;
        return recs.stream().map(r -> {
            if ("SELL".equalsIgnoreCase(r.action())) return r;
            double normalized = Math.round(r.w() * factor * 100.0) / 100.0;
            return new SecurityRecommendation(r.t(), r.n(), r.s(), normalized, r.r(), r.action());
        }).collect(Collectors.toList());
    }

    private String extractJson(String text, String provider) {
        String label = (provider == null || provider.isBlank()) ? "AI" : provider;
        if (text == null || text.isBlank()) {
            log.error("Empty {} response (rawText is null/blank)", label);
            throw new RuntimeException("Empty response from " + label);
        }
        String stripped = text.strip();
        // Strip markdown code fences ```json ... ``` or ``` ... ```
        String fence = "```";
        if (stripped.contains(fence)) {
            int openFence = stripped.indexOf(fence);
            int lineEnd = stripped.indexOf('\n', openFence + fence.length());
            int contentFrom = lineEnd >= 0 ? lineEnd + 1 : openFence + fence.length();
            int closeFence = stripped.lastIndexOf(fence);
            if (closeFence > contentFrom) {
                stripped = stripped.substring(contentFrom, closeFence).strip();
            }
        }
        int start = stripped.indexOf('[');
        int end = stripped.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            log.error("No JSON array found in {} response. Full raw text: {}", label, stripped);
            throw new RuntimeException("No JSON array found in " + label + " response: "
                    + stripped.substring(0, Math.min(500, stripped.length())));
        }
        return stripped.substring(start, end + 1);
    }

    Integer extractConfidence(String rawText) {
        if (rawText == null) return null;
        Matcher m = Pattern.compile("confidence\\s*=\\s*(\\d{1,3})").matcher(rawText);
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            return Math.min(100, Math.max(0, v));
        }
        return null;
    }

    private String nvl(String val, String def) {
        return (val != null && !val.isBlank()) ? val : def;
    }
}
