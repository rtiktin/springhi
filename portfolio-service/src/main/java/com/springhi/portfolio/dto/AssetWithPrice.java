package com.springhi.portfolio.dto;

import com.springhi.portfolio.model.Asset;
import com.springhi.portfolio.model.MarketQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AssetWithPrice {

    private Long id;
    private String symbol;
    private String companyName;
    private String assetType;
    private BigDecimal quantity;
    private BigDecimal averagePrice;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal costBasis;
    private BigDecimal gainLoss;
    private BigDecimal gainLossPercent;

    public static AssetWithPrice from(Asset asset, MarketQuote quote) {
        AssetWithPrice awp = new AssetWithPrice();
        awp.setId(asset.getId());
        awp.setSymbol(asset.getSymbol());
        awp.setCompanyName(asset.getCompanyName());
        awp.setAssetType(asset.getAssetType());
        awp.setQuantity(asset.getQuantity());
        awp.setAveragePrice(asset.getAveragePrice());

        if (quote != null && quote.getPrice() != null) {
            awp.setCurrentPrice(quote.getPrice());
            BigDecimal marketValue = asset.getQuantity().multiply(quote.getPrice());
            BigDecimal costBasis = asset.getQuantity().multiply(asset.getAveragePrice());
            BigDecimal gainLoss = marketValue.subtract(costBasis);
            awp.setMarketValue(marketValue.setScale(2, RoundingMode.HALF_UP));
            awp.setCostBasis(costBasis.setScale(2, RoundingMode.HALF_UP));
            awp.setGainLoss(gainLoss.setScale(2, RoundingMode.HALF_UP));
            if (costBasis.compareTo(BigDecimal.ZERO) != 0) {
                awp.setGainLossPercent(
                        gainLoss.divide(costBasis, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP)
                );
            }
        }
        return awp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    public BigDecimal getCostBasis() { return costBasis; }
    public void setCostBasis(BigDecimal costBasis) { this.costBasis = costBasis; }
    public BigDecimal getGainLoss() { return gainLoss; }
    public void setGainLoss(BigDecimal gainLoss) { this.gainLoss = gainLoss; }
    public BigDecimal getGainLossPercent() { return gainLossPercent; }
    public void setGainLossPercent(BigDecimal gainLossPercent) { this.gainLossPercent = gainLossPercent; }
}
