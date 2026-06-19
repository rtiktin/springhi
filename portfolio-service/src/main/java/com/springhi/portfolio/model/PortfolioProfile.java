package com.springhi.portfolio.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_profiles", schema = "springhi")
public class PortfolioProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long portfolioId;

    @Column
    private String riskLevel;

    @Column
    private String goal;

    @Column
    private Integer horizonYears;

    @Column
    private String liquidityNeeds;

    @Column(length = 2000)
    private String additionalComments;

    @Column
    private String currency = "USD";

    @Column(length = 1000)
    private String sectorConstraints;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public PortfolioProfile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public Integer getHorizonYears() { return horizonYears; }
    public void setHorizonYears(Integer horizonYears) { this.horizonYears = horizonYears; }
    public String getLiquidityNeeds() { return liquidityNeeds; }
    public void setLiquidityNeeds(String liquidityNeeds) { this.liquidityNeeds = liquidityNeeds; }
    public String getAdditionalComments() { return additionalComments; }
    public void setAdditionalComments(String additionalComments) { this.additionalComments = additionalComments; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getSectorConstraints() { return sectorConstraints; }
    public void setSectorConstraints(String sectorConstraints) { this.sectorConstraints = sectorConstraints; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
