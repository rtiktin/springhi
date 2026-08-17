package com.springhi.portfolio.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "symbol_history_status", schema = "springhi")
public class SymbolHistoryStatus {

    @Id
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "backfilled_at", nullable = false)
    private LocalDateTime backfilledAt;

    public SymbolHistoryStatus() {}

    public SymbolHistoryStatus(String symbol, LocalDateTime backfilledAt) {
        this.symbol = symbol;
        this.backfilledAt = backfilledAt;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public LocalDateTime getBackfilledAt() { return backfilledAt; }
    public void setBackfilledAt(LocalDateTime backfilledAt) { this.backfilledAt = backfilledAt; }
}
