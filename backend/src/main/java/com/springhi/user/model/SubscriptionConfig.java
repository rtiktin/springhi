package com.springhi.user.model;

import jakarta.persistence.*;

@Entity
@Table(name = "subscription_config", schema = "springhi")
public class SubscriptionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false, unique = true, length = 20)
    private String planName;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "monthly_price", nullable = false)
    private java.math.BigDecimal monthlyPrice;

    @Column(name = "annual_price", nullable = false)
    private java.math.BigDecimal annualPrice;

    @Column(name = "max_portfolios", nullable = false)
    private int maxPortfolios;

    @Column(name = "max_optimizations_per_month", nullable = false)
    private int maxOptimizationsPerMonth;

    @Column(name = "description", length = 500)
    private String description;

    public SubscriptionConfig() {}

    public Long getId() { return id; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public java.math.BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(java.math.BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public java.math.BigDecimal getAnnualPrice() { return annualPrice; }
    public void setAnnualPrice(java.math.BigDecimal annualPrice) { this.annualPrice = annualPrice; }
    public int getMaxPortfolios() { return maxPortfolios; }
    public void setMaxPortfolios(int maxPortfolios) { this.maxPortfolios = maxPortfolios; }
    public int getMaxOptimizationsPerMonth() { return maxOptimizationsPerMonth; }
    public void setMaxOptimizationsPerMonth(int maxOptimizationsPerMonth) { this.maxOptimizationsPerMonth = maxOptimizationsPerMonth; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
