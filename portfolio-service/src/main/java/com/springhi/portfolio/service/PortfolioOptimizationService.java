package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.dto.OptimizationResponse;
import com.springhi.portfolio.dto.RecommendationDto;
import com.springhi.portfolio.dto.SecurityRecommendation;
import com.springhi.portfolio.dto.UserProfileResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PortfolioOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizationService.class);

    private final UserProfileService userProfileService;
    private final GeminiService geminiService;
    private final PortfolioService portfolioService;
    private final PortfolioRecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortfolioOptimizationService(UserProfileService userProfileService,
                                        GeminiService geminiService,
                                        PortfolioService portfolioService,
                                        PortfolioRecommendationRepository recommendationRepository) {
        this.userProfileService = userProfileService;
        this.geminiService = geminiService;
        this.portfolioService = portfolioService;
        this.recommendationRepository = recommendationRepository;
    }

    public List<RecommendationDto> getTodayRecommendations(Long portfolioId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return recommendationRepository
                .findByPortfolioIdAndGeneratedAtBetweenOrderByActionDescIdAsc(portfolioId, startOfDay, endOfDay)
                .stream()
                .map(RecommendationDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public OptimizationResponse optimize(Long userId, Long portfolioId) {
        UserProfileResponse profile = userProfileService.getProfile(userId).orElse(null);
        List<AssetWithPrice> holdings = portfolioService.getUserAssetsWithPrices(portfolioId);
        BigDecimal cashBalance = portfolioService.getCashBalance(portfolioId);

        BigDecimal portfolioMarketValue = holdings.stream()
                .map(h -> h.getMarketValue() != null ? h.getMarketValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String prompt = buildPrompt(profile, holdings, cashBalance, portfolioMarketValue);
        log.info("Sending rebalancing prompt to Gemini for portfolioId={}, holdings={}, cash={}",
                portfolioId, holdings.size(), cashBalance);

        try {
            String rawText = geminiService.generateContent(prompt);
            String json = extractJson(rawText);
            List<SecurityRecommendation> recs = objectMapper.readValue(json,
                    new TypeReference<List<SecurityRecommendation>>() {});

            List<RecommendationDto> saved = persistRecommendations(userId, portfolioId, recs, holdings, cashBalance, portfolioMarketValue);
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
                                                            BigDecimal portfolioMarketValue) {
        recommendationRepository.deleteAllForPortfolio(portfolioId);

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

    private String buildPrompt(UserProfileResponse profile, List<AssetWithPrice> holdings,
                               BigDecimal cashBalance, BigDecimal portfolioMarketValue) {
        StringBuilder sb = new StringBuilder();

        sb.append("Task: Produce a portfolio rebalancing plan. Return SELL recommendations for positions to exit or trim, and BUY recommendations for new or underweight positions.\n");
        sb.append("Output: Minified JSON array only. No prose. No markdown. No code blocks.\n");
        sb.append("Keys: t (ticker), n (full name), s (sector), action (\"BUY\" or \"SELL\"), w (weight as number, see rules below), r (rationale max 8 words).\n");
        sb.append("Weight rules: For BUY entries, w = % of available cash to deploy (all BUY weights must sum to 100). For SELL entries, w = 0.\n\n");

        sb.append("Client Profile:\n");
        if (profile != null) {
            sb.append("Risk Tolerance: ").append(nvl(profile.riskLevel(), "Moderate")).append("\n");
            sb.append("Primary Objective: ").append(nvl(profile.goal(), "Growth")).append("\n");
            sb.append("Time Horizon: ").append(profile.horizonYears() != null ? profile.horizonYears() + " years" : "10+ years").append("\n");
            sb.append("Liquidity Needs: ").append(nvl(profile.liquidityNeeds(), "Low")).append("\n");
            sb.append("Knowledge Level: ").append(nvl(profile.knowledgeLevel(), "Intermediate")).append("\n");
            if (profile.additionalComments() != null && !profile.additionalComments().isBlank()) {
                sb.append("Additional Notes: ").append(profile.additionalComments()).append("\n");
            }
            if (profile.sectorConstraints() != null && !profile.sectorConstraints().isEmpty()) {
                sb.append("Preferred Sectors: ").append(String.join(", ", profile.sectorConstraints())).append("\n");
            }
        } else {
            sb.append("Risk Tolerance: Moderate\nPrimary Objective: Growth\nTime Horizon: 10+ years\n");
        }

        sb.append("\nCurrent Portfolio:\n");
        sb.append("Available Cash: $").append(cashBalance.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");
        sb.append("Total Holdings Market Value: $").append(portfolioMarketValue.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");

        if (holdings.isEmpty()) {
            sb.append("No existing holdings. This is a fresh portfolio.\n");
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

        sb.append("\nInstructions:\n");
        sb.append("1. Recommend SELL for any holdings that no longer fit the client's goals or are overweight.\n");
        sb.append("2. Recommend BUY for securities that should be added or increased to meet the client's goals.\n");
        sb.append("3. Do not recommend BUY for securities already held unless they are significantly underweight.\n");
        sb.append("4. BUY weights represent % of available cash (including expected sell proceeds) to allocate. All BUY weights must sum to exactly 100.\n");
        sb.append("5. Aim for 8-15 total recommendations (combined SELL + BUY). Diversify across sectors.\n");
        sb.append("6. If the portfolio already well matches the client profile, recommend only incremental changes.\n");

        return sb.toString();
    }

    private String extractJson(String text) {
        if (text == null) throw new RuntimeException("Empty response from Gemini");
        text = text.strip();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("No JSON array found in Gemini response: " + text.substring(0, Math.min(200, text.length())));
        }
        return text.substring(start, end + 1);
    }

    private String nvl(String val, String def) {
        return (val != null && !val.isBlank()) ? val : def;
    }
}
