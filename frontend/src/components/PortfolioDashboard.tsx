import React, { useEffect, useState } from 'react';
import { getHoldings, getPortfolioSnapshots, takePortfolioSnapshot, getCashBalance } from '../api/portfolioApi';
import type { AssetWithPrice, PortfolioSnapshot } from '../api/portfolioApi';
import { getQuoteHistory } from '../api/marketApi';
import type { QuoteResponse } from '../api/marketApi';
import StockChart from './StockChart';
import PortfolioChart from './PortfolioChart';

const fmt = (val: number | null, decimals = 2, prefix = '') =>
    val != null ? `${prefix}${val.toFixed(decimals)}` : '—';

const PortfolioDashboard: React.FC = () => {
    const [holdings, setHoldings] = useState<AssetWithPrice[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
    const [chartData, setChartData] = useState<QuoteResponse[]>([]);
    const [chartLoading, setChartLoading] = useState(false);
    const [snapshots, setSnapshots] = useState<PortfolioSnapshot[]>([]);
    const [snapshotting, setSnapshotting] = useState(false);
    const [cashBalance, setCashBalance] = useState<number | null>(null);

    useEffect(() => {
        getHoldings()
            .then(data => {
                setHoldings(data);
                if (data.length > 0) selectSymbol(data[0].symbol);
            })
            .catch(() => setError('Failed to load portfolio holdings.'))
            .finally(() => setLoading(false));

        getPortfolioSnapshots()
            .then(setSnapshots)
            .catch(() => setSnapshots([]));

        getCashBalance()
            .then(setCashBalance)
            .catch(() => setCashBalance(null));
    }, []);

    const handleSnapshotNow = () => {
        setSnapshotting(true);
        takePortfolioSnapshot()
            .then(snap => setSnapshots(prev => {
                const filtered = prev.filter(s => s.snapshotDate !== snap.snapshotDate);
                return [...filtered, snap].sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate));
            }))
            .finally(() => setSnapshotting(false));
    };

    const selectSymbol = (symbol: string) => {
        setSelectedSymbol(symbol);
        setChartLoading(true);
        getQuoteHistory(symbol)
            .then(setChartData)
            .catch(() => setChartData([]))
            .finally(() => setChartLoading(false));
    };

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
                <PortfolioChart snapshots={snapshots} />
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
                    <span className="summary-label">Total Gain / Loss</span>
                    <span className="summary-value">
                        {totalGainLoss >= 0 ? '+' : ''}{totalGainLoss.toFixed(2)} ({totalGainLossPercent.toFixed(2)}%)
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
                                className={selectedSymbol === h.symbol ? 'selected-row' : ''}
                                onClick={() => selectSymbol(h.symbol)}
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

            {selectedSymbol && (
                <div className="chart-section">
                    {chartLoading ? (
                        <div className="portfolio-loading">Loading chart…</div>
                    ) : (
                        <StockChart symbol={selectedSymbol} data={chartData} />
                    )}
                </div>
            )}
        </div>
    );
};

export default PortfolioDashboard;
