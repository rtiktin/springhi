package com.springhi.portfolio.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "user_profiles", schema = "springhi")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private String riskLevel;
    private String goal;
    private Integer horizonYears;
    private String liquidityNeeds;
    private String knowledgeLevel;

    @Column(columnDefinition = "TEXT")
    private String additionalComments;

    @Column(precision = 19, scale = 4)
    private BigDecimal availableCash;

    @Column(length = 10)
    private String currency = "USD";

    @Column(columnDefinition = "TEXT")
    private String sectorConstraints;

    public UserProfile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getGoal() { return goal; }
    public void setGoal(String goal) { this.goal = goal; }
    public Integer getHorizonYears() { return horizonYears; }
    public void setHorizonYears(Integer horizonYears) { this.horizonYears = horizonYears; }
    public String getLiquidityNeeds() { return liquidityNeeds; }
    public void setLiquidityNeeds(String liquidityNeeds) { this.liquidityNeeds = liquidityNeeds; }
    public String getKnowledgeLevel() { return knowledgeLevel; }
    public void setKnowledgeLevel(String knowledgeLevel) { this.knowledgeLevel = knowledgeLevel; }
    public String getAdditionalComments() { return additionalComments; }
    public void setAdditionalComments(String additionalComments) { this.additionalComments = additionalComments; }
    public BigDecimal getAvailableCash() { return availableCash; }
    public void setAvailableCash(BigDecimal availableCash) { this.availableCash = availableCash; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getSectorConstraints() { return sectorConstraints; }
    public void setSectorConstraints(String sectorConstraints) { this.sectorConstraints = sectorConstraints; }
}
