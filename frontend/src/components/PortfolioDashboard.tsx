import React, { useState, useEffect } from 'react';
import { getHoldings, getPortfolioSnapshots, takePortfolioSnapshot, getCashBalance, getCompanyName, getTwr, getPnlSummary } from '../api/portfolioApi';
import type { AssetWithPrice, PortfolioSnapshot, TwrResult, TwrRange, PnlSummary } from '../api/portfolioApi';
import { getQuoteHistory } from '../api/marketApi';
import type { QuoteResponse } from '../api/marketApi';
import StockChart from './StockChart';
import PortfolioChart from './PortfolioChart';

const fmt = (val: number | null, decimals = 2, prefix = '') =>
    val != null ? `${prefix}${val.toFixed(decimals)}` : '—';

interface Props {
    portfolioId: number;
}

const PortfolioDashboard: React.FC<Props> = ({ portfolioId }) => {
    const [holdings, setHoldings] = useState<AssetWithPrice[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedHolding, setSelectedHolding] = useState<AssetWithPrice | null>(null);
    const [chartData, setChartData] = useState<QuoteResponse[]>([]);
    const [chartLoading, setChartLoading] = useState(false);
    const [snapshots, setSnapshots] = useState<PortfolioSnapshot[]>([]);
    const [snapshotting, setSnapshotting] = useState(false);
    const [cashBalance, setCashBalance] = useState<number | null>(null);
    const [twrData, setTwrData] = useState<TwrResult | null>(null);
    const [twrRange, setTwrRange] = useState<TwrRange>('ALL');
    const [twrLoading, setTwrLoading] = useState(false);
    const [pnl, setPnl] = useState<PnlSummary | null>(null);

    useEffect(() => {
        setLoading(true);
        setError('');
        getHoldings(portfolioId)
            .then(data => {
                setHoldings(data);
                if (data.length > 0) selectHolding(data[0]);
            })
            .catch(() => setError('Failed to load portfolio holdings.'))
            .finally(() => setLoading(false));

        getPortfolioSnapshots(portfolioId)
            .then(setSnapshots)
            .catch(() => setSnapshots([]));

        getCashBalance(portfolioId)
            .then(setCashBalance)
            .catch(() => setCashBalance(null));

        getPnlSummary(portfolioId)
            .then(setPnl)
            .catch(() => setPnl(null));
    }, [portfolioId]);

    useEffect(() => {
        setTwrLoading(true);
        getTwr(portfolioId, twrRange)
            .then(setTwrData)
            .catch(() => setTwrData(null))
            .finally(() => setTwrLoading(false));
    }, [portfolioId, twrRange]);

    const handleSnapshotNow = () => {
        setSnapshotting(true);
        takePortfolioSnapshot(portfolioId)
            .then(snap => setSnapshots(prev => {
                const filtered = prev.filter(s => s.snapshotDate !== snap.snapshotDate);
                return [...filtered, snap].sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
            }))
            .finally(() => setSnapshotting(false));
    };

    const selectHolding = (holding: AssetWithPrice) => {
        setSelectedHolding(holding);
        setChartLoading(true);
        getQuoteHistory(holding.symbol)
            .then(setChartData)
            .catch(() => setChartData([]))
            .finally(() => setChartLoading(false));
    };

    useEffect(() => {
        if (!selectedHolding) return;
        getCompanyName(selectedHolding.symbol, portfolioId).then(name => {
            if (name) {
                setSelectedHolding(prev => prev?.symbol === selectedHolding.symbol
                    ? { ...prev, companyName: name }
                    : prev);
                setHoldings(prev => prev.map(h =>
                    h.symbol === selectedHolding.symbol ? { ...h, companyName: name } : h
                ));
            }
        }).catch(() => {});
    }, [selectedHolding?.symbol, portfolioId]);

    const totalMarketValue = holdings.reduce((sum, h) => sum + (h.marketValue ?? 0), 0);
    const totalCostBasis = holdings.reduce((sum, h) => sum + (h.costBasis ?? 0), 0);
    const totalGainLoss = totalMarketValue - totalCostBasis;
    const totalGainLossPercent = totalCostBasis !== 0 ? (totalGainLoss / totalCostBasis) * 100 : 0;

    if (loading) return <div className="portfolio-loading">Loading portfolio…</div>;
    if (error) return <div className="error-msg">{error}</div>;

    return (
        <div className="portfolio-wrapper">
            <div className="portfolio-perf-section">
                <div className="portfolio-perf-header">
                    <span className="portfolio-perf-label">Performance History</span>
                    <button
                        className="btn-snapshot"
                        onClick={handleSnapshotNow}
                        disabled={snapshotting}
                        title="Record today's portfolio value"
                    >
                        {snapshotting ? 'Saving…' : 'Snapshot Now'}
                    </button>
                </div>
                {snapshots.length >= 5 && <PortfolioChart snapshots={snapshots} />}
                {snapshots.length < 5 && snapshots.length > 0 && (
                    <p style={{ color: 'var(--text-gray)', fontSize: '0.9em', margin: '0.5rem 0' }}>
                        Portfolio chart will appear after {5 - snapshots.length} more snapshot{5 - snapshots.length !== 1 ? 's' : ''} ({snapshots.length}/5 collected).
                    </p>
                )}
            </div>

            <div className="portfolio-summary">
                <div className="summary-card">
                    <span className="summary-label">Market Value</span>
                    <span className="summary-value">${totalMarketValue.toFixed(2)}</span>
                </div>
                <div className="summary-card">
                    <span className="summary-label">Cost Basis</span>
                    <span className="summary-value">${totalCostBasis.toFixed(2)}</span>
                </div>
                <div className={`summary-card ${totalGainLoss >= 0 ? 'positive' : 'negative'}`}>
                    <span className="summary-label">Unrealized G/L</span>
                    <span className="summary-value" title="Current holdings vs cost basis">
                        {totalGainLoss >= 0 ? '+' : ''}{totalGainLoss.toFixed(2)} ({totalGainLossPercent.toFixed(2)}%)
                    </span>
                </div>
                <div className={`summary-card ${(pnl?.realizedPnl ?? 0) >= 0 ? 'positive' : 'negative'}`}>
                    <span className="summary-label">Realized G/L</span>
                    <span className="summary-value" title="Profit/loss from sold positions">
                        {pnl != null
                            ? `${pnl.realizedPnl >= 0 ? '+' : ''}$${pnl.realizedPnl.toFixed(2)}`
                            : '—'}
                    </span>
                </div>
                <div className={`summary-card ${(pnl?.totalPnl ?? 0) >= 0 ? 'positive' : 'negative'}`}>
                    <span className="summary-label">Total G/L (All-Time)</span>
                    <span className="summary-value" title="Unrealized + realized gain/loss">
                        {pnl != null
                            ? `${pnl.totalPnl >= 0 ? '+' : ''}$${pnl.totalPnl.toFixed(2)}`
                            : '—'}
                    </span>
                </div>
                <div className="summary-card">
                    <span className="summary-label">Cash Balance</span>
                    <span className="summary-value">
                        {cashBalance != null ? `$${cashBalance.toFixed(2)}` : '—'}
                    </span>
                </div>
                <div className="summary-card">
                    <span className="summary-label">Total Portfolio</span>
                    <span className="summary-value">
                        ${(totalMarketValue + (cashBalance ?? 0)).toFixed(2)}
                    </span>
                </div>
                <div className={`summary-card ${twrData && twrData.twrPercent >= 0 ? 'positive' : 'negative'}`}
                    style={{ minWidth: 200 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '0.5rem' }}>
                        <span className="summary-label">TWR</span>
                        <div style={{ display: 'flex', gap: '3px' }}>
                            {(['1M', '3M', '6M', 'YTD', '1Y', 'ALL'] as TwrRange[]).map(r => (
                                <button
                                    key={r}
                                    onClick={() => setTwrRange(r)}
                                    style={{
                                        padding: '1px 5px',
                                        fontSize: '0.68rem',
                                        fontWeight: twrRange === r ? 700 : 400,
                                        background: twrRange === r ? 'var(--accent)' : 'transparent',
                                        color: twrRange === r ? '#fff' : 'var(--text-gray)',
                                        border: '1px solid var(--border)',
                                        borderRadius: 3,
                                        cursor: 'pointer',
                                    }}
                                >{r}</button>
                            ))}
                        </div>
                    </div>
                    <span className="summary-value" style={{ fontSize: '1.2rem' }}>
                        {twrLoading ? '…' : twrData && twrData.snapshotCount >= 2
                            ? `${twrData.twrPercent >= 0 ? '+' : ''}${twrData.twrPercent.toFixed(2)}%`
                            : '—'}
                    </span>
                    {twrData && twrData.snapshotCount >= 2 && (
                        <span style={{ fontSize: '0.72rem', color: 'var(--text-gray)' }}>
                            {twrData.startDate} → {twrData.endDate}
                        </span>
                    )}
                    {twrData && twrData.snapshotCount < 2 && (
                        <span style={{ fontSize: '0.72rem', color: 'var(--text-gray)' }}>
                            Need 2+ snapshots
                        </span>
                    )}
                </div>
            </div>

            <div className="holdings-table-wrap">
                <table className="holdings-table">
                    <thead>
                        <tr>
                            <th>Symbol</th>
                            <th>Type</th>
                            <th>Qty</th>
                            <th>Avg Price</th>
                            <th>Current Price</th>
                            <th>Market Value</th>
                            <th>Cost Basis</th>
                            <th>Gain / Loss</th>
                            <th>G/L %</th>
                        </tr>
                    </thead>
                    <tbody>
                        {holdings.map(h => (
                            <tr
                                key={h.id}
                                className={selectedHolding?.symbol === h.symbol ? 'selected-row' : ''}
                                onClick={() => selectHolding(h)}
                            >
                                <td className="symbol-cell">{h.symbol}</td>
                                <td>{h.assetType}</td>
                                <td>{h.quantity}</td>
                                <td>${fmt(h.averagePrice, 4)}</td>
                                <td>{h.currentPrice != null ? `$${fmt(h.currentPrice, 4)}` : <span className="no-price">No data</span>}</td>
                                <td>{h.marketValue != null ? `$${fmt(h.marketValue)}` : '—'}</td>
                                <td>{h.costBasis != null ? `$${fmt(h.costBasis)}` : '—'}</td>
                                <td className={h.gainLoss != null && h.gainLoss >= 0 ? 'positive' : 'negative'}>
                                    {h.gainLoss != null ? `${h.gainLoss >= 0 ? '+' : ''}$${fmt(h.gainLoss)}` : '—'}
                                </td>
                                <td className={h.gainLossPercent != null && h.gainLossPercent >= 0 ? 'positive' : 'negative'}>
                                    {h.gainLossPercent != null ? `${h.gainLossPercent >= 0 ? '+' : ''}${fmt(h.gainLossPercent)}%` : '—'}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {selectedHolding && (
                <div className="chart-section">
                    {chartLoading ? (
                        <div className="portfolio-loading">Loading chart…</div>
                    ) : (
                        <StockChart
                            symbol={selectedHolding.symbol}
                            companyName={selectedHolding.companyName ?? undefined}
                            data={chartData}
                        />
                    )}
                </div>
            )}
        </div>
    );
};

export default PortfolioDashboard;
