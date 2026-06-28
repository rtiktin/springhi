import React, { useState, useEffect } from 'react';
import { getTransactions, getCompanyName, getAiRunDetails } from '../api/portfolioApi';
import type { Transaction, AiRunDetails } from '../api/portfolioApi';

interface Props {
    portfolioId: number;
}

const fmt = (n: number | null) =>
    n != null ? `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';

const TransactionHistory: React.FC<Props> = ({ portfolioId }) => {
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
    const [companyNameMap, setCompanyNameMap] = useState<Record<string, string | null>>({});
    const [lookingUp, setLookingUp] = useState(false);

    const [aiRunModal, setAiRunModal] = useState<{ details: AiRunDetails; highlightTxnId: number } | null>(null);
    const [aiRunLoading, setAiRunLoading] = useState(false);
    const [aiRunError, setAiRunError] = useState('');

    useEffect(() => {
        setLoading(true);
        getTransactions(portfolioId)
            .then(data => setTransactions(data.sort((a, b) =>
                new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
            )))
            .catch(() => setError('Failed to load transaction history.'))
            .finally(() => setLoading(false));
    }, [portfolioId]);

    const handleSymbolClick = (symbol: string) => {
        if (!symbol || symbol === 'CASH') return;
        setSelectedSymbol(symbol);
        setLookingUp(true);
        getCompanyName(symbol, portfolioId)
            .then(name => setCompanyNameMap(prev => ({ ...prev, [symbol]: name })))
            .catch(() => setCompanyNameMap(prev => ({ ...prev, [symbol]: null })))
            .finally(() => setLookingUp(false));
    };

    const handleAiBadgeClick = (txn: Transaction) => {
        if (!txn.aiRunGeneratedAt) return;
        setAiRunLoading(true);
        setAiRunError('');
        getAiRunDetails(portfolioId, txn.aiRunGeneratedAt)
            .then(details => setAiRunModal({ details, highlightTxnId: txn.id }))
            .catch(() => setAiRunError('Failed to load AI run details.'))
            .finally(() => setAiRunLoading(false));
    };

    if (loading) return <div className="portfolio-loading">Loading transactions…</div>;
    if (error) return <div className="error-msg">{error}</div>;
    if (transactions.length === 0) return (
        <div className="portfolio-loading">No transactions yet. Place your first trade!</div>
    );

    const resolvedName = selectedSymbol != null ? companyNameMap[selectedSymbol] : undefined;
    const profile = aiRunModal?.details.profile;

    return (
        <div>
            {selectedSymbol && (
                <div style={{
                    padding: '0.5rem 1rem',
                    marginBottom: '0.75rem',
                    background: 'var(--bg-card)',
                    borderRadius: '6px',
                    fontSize: '0.9rem',
                    color: 'var(--text-gray)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                }}>
                    <span className="symbol-cell">{selectedSymbol}</span>
                    {lookingUp
                        ? <span>Looking up…</span>
                        : resolvedName
                            ? <span style={{ color: 'var(--text-primary)' }}>{resolvedName}</span>
                            : resolvedName === null
                                ? <span>Company name not found</span>
                                : null
                    }
                </div>
            )}

            {aiRunLoading && (
                <div className="portfolio-loading" style={{ marginBottom: '0.5rem' }}>Loading AI run details…</div>
            )}
            {aiRunError && (
                <div className="error-msg" style={{ marginBottom: '0.5rem' }}>{aiRunError}</div>
            )}

            <div className="holdings-table-wrap">
                <table className="holdings-table">
                    <thead>
                        <tr>
                            <th>Date &amp; Time</th>
                            <th>Symbol</th>
                            <th>Type</th>
                            <th>Quantity</th>
                            <th>Price</th>
                            <th>Total Value</th>
                            <th>AI</th>
                        </tr>
                    </thead>
                    <tbody>
                        {transactions.map(t => (
                            <tr key={t.id}>
                                <td>{new Date(t.timestamp).toLocaleString()}</td>
                                <td
                                    className="symbol-cell"
                                    style={t.symbol !== 'CASH' ? { cursor: 'pointer', textDecoration: 'underline dotted' } : {}}
                                    title={t.symbol !== 'CASH' ? 'Click to look up company name' : undefined}
                                    onClick={() => handleSymbolClick(t.symbol)}
                                >
                                    {t.symbol}
                                </td>
                                <td className={t.type === 'BUY' ? 'positive' : 'negative'}>{t.type}</td>
                                <td>{t.quantity}</td>
                                <td>${t.price.toFixed(4)}</td>
                                <td>${(t.quantity * t.price).toFixed(2)}</td>
                                <td>
                                    {t.aiRunGeneratedAt ? (
                                        <button
                                            title="AI-generated trade — click to view the full optimization run"
                                            onClick={() => handleAiBadgeClick(t)}
                                            style={{
                                                background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
                                                color: '#fff',
                                                border: 'none',
                                                borderRadius: '4px',
                                                padding: '2px 7px',
                                                fontSize: '0.72rem',
                                                fontWeight: 700,
                                                cursor: 'pointer',
                                                letterSpacing: '0.04em',
                                            }}
                                        >
                                            {(() => {
                                                const p = t.aiProvider?.toLowerCase();
                                                if (p === 'claude') return 'Claude';
                                                if (p === 'chatgpt') return 'ChatGPT';
                                                if (p === 'gemini') return 'Gemini';
                                                return 'AI';
                                            })()}
                                        </button>
                                    ) : null}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {aiRunModal && (
                <div className="modal-overlay" onClick={() => setAiRunModal(null)}>
                    <div
                        className="modal-card"
                        onClick={e => e.stopPropagation()}
                        style={{ maxWidth: 780, width: '95%', maxHeight: '85vh', overflowY: 'auto' }}
                    >
                        <div className="modal-header">
                            <h2>AI Optimization Run</h2>
                            <button className="modal-close" onClick={() => setAiRunModal(null)}>✕</button>
                        </div>

                        <p style={{ color: 'var(--text-gray)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                            Generated{' '}
                            {new Date(aiRunModal.details.recommendations[0]?.generatedAt ?? '').toLocaleString()}
                            {(() => {
                                const provider = aiRunModal.details.recommendations[0]?.aiProvider;
                                if (!provider) return null;
                                const p = provider.toLowerCase();
                                const label = p === 'claude' ? 'Claude' : p === 'chatgpt' ? 'ChatGPT' : 'Gemini';
                                return <span style={{ marginLeft: '0.75rem', background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 600 }}>via {label}</span>;
                            })()}
                        </p>

                        <h3 style={{ color: 'var(--text-light)', marginBottom: '0.5rem', fontSize: '0.95rem' }}>
                            Trades in this run
                        </h3>
                        <div className="holdings-table-wrap" style={{ marginBottom: '1.5rem' }}>
                            <table className="holdings-table">
                                <thead>
                                    <tr>
                                        <th>Action</th>
                                        <th>Symbol</th>
                                        <th>Name</th>
                                        <th>Weight</th>
                                        <th>Est. Amount</th>
                                        <th>Status</th>
                                        <th>Rationale</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {aiRunModal.details.recommendations.map(rec => {
                                        const isHighlighted = rec.transactionId === aiRunModal.highlightTxnId;
                                        return (
                                            <tr
                                                key={rec.id}
                                                style={isHighlighted ? {
                                                    background: 'rgba(99,102,241,0.25)',
                                                    outline: '2px solid #6366f1',
                                                } : {}}
                                            >
                                                <td className={rec.action === 'BUY' ? 'positive' : 'negative'}
                                                    style={{ fontWeight: isHighlighted ? 700 : undefined }}>
                                                    {rec.action}
                                                </td>
                                                <td className="symbol-cell">{rec.t}</td>
                                                <td style={{ fontSize: '0.82rem', color: 'var(--text-gray)' }}>{rec.n}</td>
                                                <td>{rec.w.toFixed(1)}%</td>
                                                <td>{fmt(rec.estimatedValue)}</td>
                                                <td style={{
                                                    color: rec.status === 'EXECUTED' ? '#22c55e'
                                                        : rec.status === 'SKIPPED' ? '#f59e0b'
                                                            : 'var(--text-gray)',
                                                    fontWeight: 600,
                                                    fontSize: '0.82rem',
                                                }}>
                                                    {rec.status}
                                                </td>
                                                <td style={{ fontSize: '0.78rem', color: 'var(--text-gray)', maxWidth: 200 }}>
                                                    {rec.r}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>

                        {profile && (
                            <>
                                <h3 style={{ color: 'var(--text-light)', marginBottom: '0.75rem', fontSize: '0.95rem' }}>
                                    Portfolio Profile used for this optimization
                                </h3>
                                <div style={{
                                    display: 'grid',
                                    gridTemplateColumns: '1fr 1fr',
                                    gap: '0.5rem 1.5rem',
                                    background: 'var(--bg-card)',
                                    borderRadius: 8,
                                    padding: '1rem',
                                    fontSize: '0.88rem',
                                }}>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Risk Tolerance: </span>
                                        <strong>{profile.riskLevel?.replace('_', ' ') ?? 'N/A'}</strong></div>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Primary Goal: </span>
                                        <strong>{profile.goal ?? 'N/A'}</strong></div>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Time Horizon: </span>
                                        <strong>{profile.horizonYears != null ? `${profile.horizonYears} years` : 'N/A'}</strong></div>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Liquidity Needs: </span>
                                        <strong>{profile.liquidityNeeds ?? 'N/A'}</strong></div>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Currency: </span>
                                        <strong>{profile.currency}</strong></div>
                                    <div><span style={{ color: 'var(--text-gray)' }}>Preferred Sectors: </span>
                                        <strong>{profile.sectorConstraints?.length ? profile.sectorConstraints.join(', ') : 'None'}</strong></div>
                                    <div style={{ gridColumn: '1 / -1' }}>
                                        <span style={{ color: 'var(--text-gray)' }}>Additional Notes: </span>
                                        <span>{profile.additionalComments ?? 'None'}</span>
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default TransactionHistory;
