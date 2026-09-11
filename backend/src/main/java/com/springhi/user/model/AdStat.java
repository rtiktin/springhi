package com.springhi.user.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ad_stats",
        schema = "springhi",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ad_id", "stat_date"})
)
public class AdStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    // The day these metrics are for. One row per ad per day (append-only daily snapshot).
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "impressions", nullable = false)
    private long impressions;

    @Column(name = "clicks", nullable = false)
    private long clicks;

    @Column(name = "spend", nullable = false, precision = 12, scale = 2)
    private BigDecimal spend;

    @Column(name = "conversions", nullable = false)
    private int conversions;

    @Column(name = "revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public AdStat() {}

    public Long getId() { return id; }
    public Long getAdId() { return adId; }
    public void setAdId(Long adId) { this.adId = adId; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public long getImpressions() { return impressions; }
    public void setImpressions(long impressions) { this.impressions = impressions; }
    public long getClicks() { return clicks; }
    public void setClicks(long clicks) { this.clicks = clicks; }
    public BigDecimal getSpend() { return spend; }
    public void setSpend(BigDecimal spend) { this.spend = spend; }
    public int getConversions() { return conversions; }
    public void setConversions(int conversions) { this.conversions = conversions; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
