package com.springhi.portfolio.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividend_events", schema = "springhi",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "symbol", "ex_date"}))
public class DividendEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "dividend_per_share", nullable = false, precision = 19, scale = 6)
    private BigDecimal dividendPerShare;

    @Column(name = "shares_held", nullable = false, precision = 19, scale = 4)
    private BigDecimal sharesHeld;

    @Column(name = "total_credited", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredited;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public DividendEvent() {
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long portfolioId) { this.portfolioId = portfolioId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDate getExDate() { return exDate; }
    public void setExDate(LocalDate exDate) { this.exDate = exDate; }
    public BigDecimal getDividendPerShare() { return dividendPerShare; }
    public void setDividendPerShare(BigDecimal dividendPerShare) { this.dividendPerShare = dividendPerShare; }
    public BigDecimal getSharesHeld() { return sharesHeld; }
    public void setSharesHeld(BigDecimal sharesHeld) { this.sharesHeld = sharesHeld; }
    public BigDecimal getTotalCredited() { return totalCredited; }
    public void setTotalCredited(BigDecimal totalCredited) { this.totalCredited = totalCredited; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
