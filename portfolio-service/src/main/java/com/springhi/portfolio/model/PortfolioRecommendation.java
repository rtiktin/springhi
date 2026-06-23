package com.springhi.portfolio.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_recommendations", schema = "springhi")
public class PortfolioRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column
    private Long portfolioId;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private String ticker;

    private String name;

    private String sector;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal weight;

    @Column(length = 1000)
    private String rationale;

    @Column(nullable = false)
    private String status = "PENDING";

    private Long transactionId;

    private LocalDateTime executedAt;

    @Column(precision = 19, scale = 2)
    private BigDecimal estimatedValue;

    @Column(length = 50)
    private String snapshotRiskLevel;

    @Column(length = 50)
    private String snapshotGoal;

    @Column
    private Integer snapshotHorizonYears;

    @Column(length = 50)
    private String snapshotLiquidityNeeds;

    @Column(length = 2000)
    private String snapshotAdditionalComments;

    @Column(length = 10)
    private String snapshotCurrency;

    @Column(length = 1000)
    private String snapshotSectorConstraints;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PortfolioRecommendation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public BigDecimal getEstimatedValue() { return estimatedValue; }
    public void setEstimatedValue(BigDecimal estimatedValue) { this.estimatedValue = estimatedValue; }
    public String getSnapshotRiskLevel() { return snapshotRiskLevel; }
    public void setSnapshotRiskLevel(String snapshotRiskLevel) { this.snapshotRiskLevel = snapshotRiskLevel; }
    public String getSnapshotGoal() { return snapshotGoal; }
    public void setSnapshotGoal(String snapshotGoal) { this.snapshotGoal = snapshotGoal; }
    public Integer getSnapshotHorizonYears() { return snapshotHorizonYears; }
    public void setSnapshotHorizonYears(Integer snapshotHorizonYears) { this.snapshotHorizonYears = snapshotHorizonYears; }
    public String getSnapshotLiquidityNeeds() { return snapshotLiquidityNeeds; }
    public void setSnapshotLiquidityNeeds(String snapshotLiquidityNeeds) { this.snapshotLiquidityNeeds = snapshotLiquidityNeeds; }
    public String getSnapshotAdditionalComments() { return snapshotAdditionalComments; }
    public void setSnapshotAdditionalComments(String snapshotAdditionalComments) { this.snapshotAdditionalComments = snapshotAdditionalComments; }
    public String getSnapshotCurrency() { return snapshotCurrency; }
    public void setSnapshotCurrency(String snapshotCurrency) { this.snapshotCurrency = snapshotCurrency; }
    public String getSnapshotSectorConstraints() { return snapshotSectorConstraints; }
    public void setSnapshotSectorConstraints(String snapshotSectorConstraints) { this.snapshotSectorConstraints = snapshotSectorConstraints; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
