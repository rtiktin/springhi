package com.springhi.portfolio.service;

import com.springhi.portfolio.dto.AssetWithPrice;
import com.springhi.portfolio.model.Asset;
import com.springhi.portfolio.model.Transaction;
import com.springhi.portfolio.repository.AssetRepository;
import com.springhi.portfolio.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class PortfolioService {
    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final MarketDataService marketDataService;
    private final AlpacaService alpacaService;

    public PortfolioService(AssetRepository assetRepository,
                            TransactionRepository transactionRepository,
                            MarketDataService marketDataService,
                            AlpacaService alpacaService) {
        this.assetRepository = assetRepository;
        this.transactionRepository = transactionRepository;
        this.marketDataService = marketDataService;
        this.alpacaService = alpacaService;
    }

    public List<Asset> getUserAssets(Long userId) {
        return assetRepository.findByUserId(userId);
    }

    public List<AssetWithPrice> getUserAssetsWithPrices(Long userId) {
        return assetRepository.findByUserId(userId).stream()
                .map(asset -> AssetWithPrice.from(
                        asset,
                        marketDataService.getLatestCachedQuote(asset.getSymbol()).orElse(null)
                ))
                .toList();
    }

    public List<Transaction> getUserTransactions(Long userId) {
        return transactionRepository.findByUserId(userId);
    }

    public BigDecimal getCashBalance(Long userId) {
        return transactionRepository.computeCashBalance(userId);
    }

    @Transactional
    public Optional<String> getOrFetchCompanyName(Long userId, String symbol) {
        Optional<Asset> assetOpt = assetRepository.findByUserIdAndSymbol(userId, symbol);
        if (assetOpt.isPresent() && assetOpt.get().getCompanyName() != null
                && !assetOpt.get().getCompanyName().isBlank()) {
            return Optional.of(assetOpt.get().getCompanyName());
        }
        Optional<String> fetched = alpacaService.fetchCompanyName(symbol);
        fetched.ifPresent(name -> {
            assetOpt.ifPresent(asset -> {
                asset.setCompanyName(name);
                assetRepository.save(asset);
            });
        });
        return fetched;
    }

    @Transactional
    public Transaction processTransaction(Transaction transaction) {
        String type = transaction.getType();

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

        Asset asset = assetRepository.findByUserIdAndSymbol(transaction.getUserId(), transaction.getSymbol())
                .orElse(new Asset());

        if (asset.getId() == null) {
            asset.setUserId(transaction.getUserId());
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
