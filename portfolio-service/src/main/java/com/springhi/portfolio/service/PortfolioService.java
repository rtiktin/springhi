package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.model.Asset;
import com.springhi.portfolio.model.Portfolio;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.repository.PortfolioRepository;
import com.springhi.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springhi.portfolio.model.MarketQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PortfolioService {
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final AlpacaService alpacaService;
    private final PortfolioRepository portfolioRepository;

    public PortfolioService(AssetRepository assetRepository,
                            TransactionRepository transactionRepository,
                            MarketDataService marketDataService,
                            AlpacaService alpacaService,
                            PortfolioRepository portfolioRepository) {
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.alpacaService = alpacaService;
        this.portfolioRepository = portfolioRepository;
    }

    public List<Portfolio> listPortfolios(Long userId) {
        return portfolioRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public Portfolio getOrCreateDefaultPortfolio(Long userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserIdOrderByCreatedAtAsc(userId);
        if (!portfolios.isEmpty()) return portfolios.get(0);
        Portfolio p = new Portfolio();
        p.setUserId(userId);
        p.setName("Default Portfolio");
        return portfolioRepository.save(p);
    }

    public Portfolio createPortfolio(Long userId, String name, String description) {
        Portfolio p = new Portfolio();
        p.setUserId(userId);
        p.setName(name);
        p.setDescription(description);
        return portfolioRepository.save(p);
    }

    public Portfolio updatePortfolio(Long userId, Long portfolioId, String name, String description) {
        Portfolio p = validatePortfolioOwnership(userId, portfolioId);
        if (name != null && !name.isBlank()) p.setName(name);
        p.setDescription(description);
        return portfolioRepository.save(p);
    }

    public void deletePortfolio(Long userId, Long portfolioId) {
        Portfolio p = validatePortfolioOwnership(userId, portfolioId);
        portfolioRepository.delete(p);
    }

    public Portfolio validatePortfolioOwnership(Long userId, Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new SecurityException("Portfolio not found or access denied"));
    }

    public List<Asset> getUserAssets(Long portfolioId) {
        return assetRepository.findByPortfolioId(portfolioId);
    }

    public List<AssetWithPrice> getUserAssetsWithPrices(Long portfolioId) {
        List<Asset> assets = assetRepository.findByPortfolioId(portfolioId);
        if (assets.isEmpty()) return List.of();
        List<String> symbols = assets.stream().map(Asset::getSymbol).distinct().toList();
        Map<String, MarketQuote> freshQuotes = marketDataService.refreshQuotes(symbols);
        return assets.stream()
                .map(asset -> AssetWithPrice.from(
                        asset,
                        freshQuotes.getOrDefault(asset.getSymbol(),
                                marketDataService.getLatestCachedQuote(asset.getSymbol()).orElse(null))
                ))
                .toList();
    }

    public List<Transaction> getUserTransactions(Long portfolioId) {
        return transactionRepository.findByPortfolioId(portfolioId);
    }

    public BigDecimal getCashBalance(Long portfolioId) {
        return transactionRepository.computeCashBalanceByPortfolio(portfolioId);
    }

    @Transactional
    public Optional<String> getOrFetchCompanyName(Long portfolioId, String symbol) {
        Optional<Asset> assetOpt = assetRepository.findByPortfolioIdAndSymbol(portfolioId, symbol);
        if (assetOpt.isPresent() && assetOpt.get().getCompanyName() != null
                && !assetOpt.get().getCompanyName().isBlank()) {
            return Optional.of(assetOpt.get().getCompanyName());
        }
        Optional<String> fetched = alpacaService.fetchCompanyName(symbol);
        fetched.ifPresent(name -> assetOpt.ifPresent(asset -> {
            asset.setCompanyName(name);
            assetRepository.save(asset);
        }));
        return fetched;
    }

    @Transactional
    public Transaction processTransaction(Transaction transaction) {
        String type = transaction.getType();
        Long portfolioId = transaction.getPortfolioId();

        if ("DEPOSIT".equals(type) || "WITHDRAWAL".equals(type)) {
            if (transaction.getSymbol() == null || transaction.getSymbol().isBlank()) {
                transaction.setSymbol("CASH");
            }
            if (transaction.getPrice() == null || transaction.getPrice().compareTo(BigDecimal.ONE) != 0) {
                transaction.setPrice(BigDecimal.ONE);
            }
            return transactionRepository.save(transaction);
        }

        if (!"BUY".equals(type) && !"SELL".equals(type)) {
            throw new IllegalArgumentException("Invalid transaction type: " + type);
        }

        Optional<Asset> existingAsset = portfolioId != null
                ? assetRepository.findByPortfolioIdAndSymbol(portfolioId, transaction.getSymbol())
                : assetRepository.findByUserIdAndSymbol(transaction.getUserId(), transaction.getSymbol());

        Asset asset = existingAsset.orElse(new Asset());

        if (asset.getId() == null) {
            asset.setUserId(transaction.getUserId());
            asset.setPortfolioId(portfolioId);
            asset.setSymbol(transaction.getSymbol());
            asset.setQuantity(BigDecimal.ZERO);
            asset.setAveragePrice(BigDecimal.ZERO);
            asset.setAssetType("STOCK");
            alpacaService.fetchCompanyName(transaction.getSymbol())
                    .ifPresent(asset::setCompanyName);
        }

        if ("BUY".equals(type)) {
            BigDecimal currentTotalCost = asset.getQuantity().multiply(asset.getAveragePrice());
            BigDecimal transactionTotalCost = transaction.getQuantity().multiply(transaction.getPrice());
            BigDecimal newQuantity = asset.getQuantity().add(transaction.getQuantity());
            BigDecimal newTotalCost = currentTotalCost.add(transactionTotalCost);
            asset.setQuantity(newQuantity);
            asset.setAveragePrice(newTotalCost.divide(newQuantity, 4, RoundingMode.HALF_UP));
        } else {
            if (asset.getQuantity().compareTo(transaction.getQuantity()) < 0) {
                throw new IllegalStateException("Insufficient asset quantity to sell");
            }
            asset.setQuantity(asset.getQuantity().subtract(transaction.getQuantity()));
        }

        if (asset.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            if (asset.getId() != null) assetRepository.delete(asset);
        } else {
            assetRepository.save(asset);
        }

        return transactionRepository.save(transaction);
    }
}
