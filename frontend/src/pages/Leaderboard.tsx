import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
    getLeaderboard,
    getLeaderboardPortfolioHoldings,
    getLeaderboardPortfolioTransactions,
    getLeaderboardAiRunDetails,
} from '../api/portfolioApi';
import type { LeaderboardEntry, AssetWithPrice, Transaction, AiRunDetails } from '../api/portfolioApi';
import { getLoggedInUsername } from '../utils/auth';

const isLoggedIn = () => !!localStorage.getItem('token');

type LeaderboardRange = '1W' | '1M' | '3M' | '6M' | '1Y';
type LeaderboardScope = 'mine' | 'all';
type DetailTab = 'holdings' | 'transactions';

const RANGE_LABELS: Record<LeaderboardRange, string> = {
    '1W': '1 Week',
    '1M': '1 Month',
    '3M': '3 Months',
    '6M': '6 Months',
    '1Y': '1 Year',
};

const rankMedal = (rank: number) => {
    if (rank === 1) return '🥇';
    if (rank === 2) return '🥈';
    if (rank === 3) return '🥉';
    return `#${rank}`;
};

const fmt = (n: number | null) =>
    n != null ? `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';

const EmptyState: React.FC = () => (
    <div style={{
        textAlign: 'center', color: 'var(--text-gray)', padding: '3rem',
        background: 'var(--bg-card)', borderRadius: '12px', border: '1px solid var(--border)',
    }}>
        <p style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>No eligible portfolios yet</p>
        <p style={{ fontSize: '0.9rem' }}>Portfolios need at least 5 holdings and 2 daily snapshots to qualify.</p>
    </div>
);

const thStyle: React.CSSProperties = {
    padding: '0.75rem 1rem', textAlign: 'left', fontSize: '0.78rem',
    textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--text-gray)',
};

interface PortfolioDetailModalProps {
    entry: LeaderboardEntry;
    onClose: () => void;
}

const aiBadgeLabel = (provider: string | null) => {
    const p = provider?.toLowerCase();
    if (p === 'claude') return 'Claude';
    if (p === 'chatgpt') return 'ChatGPT';
    if (p === 'gemini') return 'Gemini';
    return 'AI';
};

const PortfolioDetailModal: React.FC<PortfolioDetailModalProps> = ({ entry, onClose }) => {
    const [tab, setTab] = useState<DetailTab>('holdings');
    const [holdings, setHoldings] = useState<AssetWithPrice[]>([]);
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(true);

    const [aiRunModal, setAiRunModal] = useState<{ details: AiRunDetails; highlightTxnId: number } | null>(null);
    const [aiRunLoading, setAiRunLoading] = useState(false);
    const [aiRunError, setAiRunError] = useState('');

    useEffect(() => {
        setLoading(true);
        Promise.all([
            getLeaderboardPortfolioHoldings(entry.portfolioId),
            getLeaderboardPortfolioTransactions(entry.portfolioId),
        ]).then(([h, t]) => {
            setHoldings(h);
            setTransactions(t.slice().reverse());
        }).finally(() => setLoading(false));
    }, [entry.portfolioId]);

    const handleAiBadgeClick = (txn: Transaction) => {
        if (!txn.aiRunGeneratedAt) return;
        setAiRunLoading(true);
        setAiRunError('');
        getLeaderboardAiRunDetails(entry.portfolioId, txn.aiRunGeneratedAt)
            .then(details => setAiRunModal({ details, highlightTxnId: txn.id }))
            .catch(() => setAiRunError('Failed to load AI run details.'))
            .finally(() => setAiRunLoading(false));
    };

    const profile = aiRunModal?.details.profile;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-card" onClick={e => e.stopPropagation()}
                style={{ maxWidth: 820, width: '95%', maxHeight: '85vh', overflowY: 'auto' }}>
                <div className="modal-header">
                    <h2 style={{ fontSize: '1.15rem' }}>
                        {entry.portfolioName}
                        {entry.username && <span style={{ color: 'var(--text-gray)', fontWeight: 400, fontSize: '0.9rem', marginLeft: '0.6rem' }}>({entry.username})</span>}
                    </h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>

                <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', marginBottom: '1rem' }}>
                    {(['holdings', 'transactions'] as DetailTab[]).map(t => (
                        <button key={t} onClick={() => setTab(t)} style={{
                            padding: '0.5rem 1.25rem', border: 'none',
                            borderBottom: tab === t ? '2px solid var(--accent)' : '2px solid transparent',
                            background: 'transparent',
                            color: tab === t ? 'var(--accent)' : 'var(--text-gray)',
                            fontWeight: tab === t ? 700 : 400, cursor: 'pointer',
                            fontSize: '0.9rem', marginBottom: '-1px',
                        }}>
                            {t.charAt(0).toUpperCase() + t.slice(1)}
                        </button>
                    ))}
                </div>

                {aiRunLoading && (
                    <div className="portfolio-loading" style={{ marginBottom: '0.5rem' }}>Loading AI run details…</div>
                )}
                {aiRunError && (
                    <div className="error-msg" style={{ marginBottom: '0.5rem' }}>{aiRunError}</div>
                )}

                {loading ? (
                    <div style={{ textAlign: 'center', color: 'var(--text-gray)', padding: '2rem' }}>Loading…</div>
                ) : tab === 'holdings' ? (
                    <div className="holdings-table-wrap">
                        <table className="holdings-table">
                            <thead>
                                <tr>
                                    <th>Symbol</th>
                                    <th>Company</th>
                                    <th style={{ textAlign: 'right' }}>Qty</th>
                                    <th style={{ textAlign: 'right' }}>Price</th>
                                    <th style={{ textAlign: 'right' }}>Market Value</th>
                                    <th style={{ textAlign: 'right' }}>Gain/Loss</th>
                                </tr>
                            </thead>
                            <tbody>
                                {holdings.map(h => (
                                    <tr key={h.id}>
                                        <td style={{ fontWeight: 700 }}>{h.symbol}</td>
                                        <td style={{ color: 'var(--text-gray)', fontSize: '0.85rem' }}>{h.companyName ?? '—'}</td>
                                        <td style={{ textAlign: 'right' }}>{h.quantity}</td>
                                        <td style={{ textAlign: 'right' }}>{fmt(h.currentPrice)}</td>
                                        <td style={{ textAlign: 'right' }}>{fmt(h.marketValue)}</td>
                                        <td style={{
                                            textAlign: 'right',
                                            color: h.gainLoss != null && h.gainLoss >= 0 ? '#34d399' : '#f87171',
                                            fontWeight: 600,
                                        }}>
                                            {h.gainLoss != null ? `${h.gainLoss >= 0 ? '+' : ''}${fmt(h.gainLoss)}` : '—'}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <div className="holdings-table-wrap">
                        <table className="holdings-table">
                            <thead>
                                <tr>
                                    <th>Date</th>
                                    <th>Symbol</th>
                                    <th>Type</th>
                                    <th style={{ textAlign: 'right' }}>Qty</th>
                                    <th style={{ textAlign: 'right' }}>Price</th>
                                    <th style={{ textAlign: 'right' }}>Total</th>
                                    <th>AI</th>
                                </tr>
                            </thead>
                            <tbody>
                                {transactions.map(t => (
                                    <tr key={t.id}>
                                        <td style={{ fontSize: '0.82rem', color: 'var(--text-gray)' }}>
                                            {new Date(t.timestamp).toLocaleDateString()}
                                        </td>
                                        <td style={{ fontWeight: 700 }}>{t.symbol}</td>
                                        <td style={{ color: t.type === 'BUY' ? '#34d399' : t.type === 'SELL' ? '#f87171' : 'var(--text-gray)' }}>
                                            {t.type}
                                        </td>
                                        <td style={{ textAlign: 'right' }}>{t.quantity}</td>
                                        <td style={{ textAlign: 'right' }}>${t.price.toFixed(4)}</td>
                                        <td style={{ textAlign: 'right' }}>${(t.quantity * t.price).toFixed(2)}</td>
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
                                                    {aiBadgeLabel(t.aiProvider)}
                                                </button>
                                            ) : null}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
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
                                const label = aiBadgeLabel(provider);
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

interface LeaderboardTableProps {
    entries: LeaderboardEntry[];
    range: LeaderboardRange;
    showUser: boolean;
}

const LeaderboardTable: React.FC<LeaderboardTableProps> = ({ entries, range, showUser }) => {
    const [selectedEntry, setSelectedEntry] = useState<LeaderboardEntry | null>(null);

    return (
        <>
            <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead>
                        <tr style={{ background: 'rgba(255,255,255,0.04)', borderBottom: '1px solid var(--border)' }}>
                            <th style={thStyle}>Rank</th>
                            {showUser && <th style={thStyle}>Username</th>}
                            <th style={thStyle}>Portfolio</th>
                            <th style={{ ...thStyle, textAlign: 'right' }}>TWR ({RANGE_LABELS[range]})</th>
                            <th style={{ ...thStyle, textAlign: 'right' }}>Holdings</th>
                            <th style={{ ...thStyle, textAlign: 'right' }}>Max Position</th>
                        </tr>
                    </thead>
                    <tbody>
                        {entries.map((entry, idx) => (
                            <tr
                                key={entry.portfolioId}
                                onClick={() => setSelectedEntry(entry)}
                                style={{
                                    borderBottom: idx < entries.length - 1 ? '1px solid var(--border)' : 'none',
                                    background: entry.rank <= 3 ? 'rgba(99,102,241,0.05)' : 'transparent',
                                    cursor: 'pointer',
                                    transition: 'background 0.12s',
                                }}
                                onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.04)')}
                                onMouseLeave={e => (e.currentTarget.style.background = entry.rank <= 3 ? 'rgba(99,102,241,0.05)' : 'transparent')}
                            >
                                <td style={{ padding: '1rem 1.25rem', fontSize: '1.1rem', fontWeight: 700 }}>
                                    {rankMedal(entry.rank)}
                                </td>
                                {showUser && (
                                    <td style={{ padding: '1rem 1.25rem', color: 'var(--text-gray)', fontSize: '0.9rem' }}>
                                        {entry.username ?? '—'}
                                    </td>
                                )}
                                <td style={{ padding: '1rem 1.25rem' }}>
                                    <span style={{ fontWeight: 600, color: 'var(--accent)', textDecoration: 'underline', cursor: 'pointer' }}>
                                        {entry.portfolioName}
                                    </span>
                                </td>
                                <td style={{
                                    padding: '1rem 1.25rem', textAlign: 'right', fontWeight: 700, fontSize: '1.05rem',
                                    color: entry.twrPercent >= 0 ? '#34d399' : '#f87171',
                                }}>
                                    {entry.twrPercent >= 0 ? '+' : ''}{entry.twrPercent.toFixed(2)}%
                                </td>
                                <td style={{ padding: '1rem 1.25rem', textAlign: 'right', color: 'var(--text-gray)' }}>
                                    {entry.holdingCount}
                                </td>
                                <td style={{ padding: '1rem 1.25rem', textAlign: 'right', color: 'var(--text-gray)' }}>
                                    {entry.maxHoldingPct.toFixed(1)}%
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {selectedEntry && (
                <PortfolioDetailModal entry={selectedEntry} onClose={() => setSelectedEntry(null)} />
            )}
        </>
    );
};

const LeaderboardPane: React.FC<{ scope: LeaderboardScope; range: LeaderboardRange }> = ({ scope, range }) => {
    const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        setLoading(true);
        setError('');
        getLeaderboard(range, scope)
            .then(setEntries)
            .catch(() => setError('Failed to load leaderboard. Please try again.'))
            .finally(() => setLoading(false));
    }, [scope, range]);

    if (loading) return <div style={{ textAlign: 'center', color: 'var(--text-gray)', padding: '3rem' }}>Loading…</div>;
    if (error) return <div style={{ textAlign: 'center', color: '#f87171', padding: '1rem' }}>{error}</div>;
    if (entries.length === 0) return <EmptyState />;
    return <LeaderboardTable entries={entries} range={range} showUser={scope === 'all'} />;
};

const Leaderboard: React.FC = () => {
    const [range, setRange] = useState<LeaderboardRange>('1M');
    const [scope, setScope] = useState<LeaderboardScope>('all');
    const username = getLoggedInUsername();

    return (
        <div className="home-container">
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/portfolio" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    {isLoggedIn() ? (
                        <>
                            <Link to="/account" className="btn-logout">Account</Link>
                            <Link to="/portfolio" className="btn-logout">My Portfolios</Link>
                        </>
                    ) : (
                        <>
                            <Link to="/login" className="nav-link">Login</Link>
                            <Link to="/signup" className="btn-primary">Get Started</Link>
                        </>
                    )}
                </nav>
            </header>

            <main style={{ padding: '4rem 10%', maxWidth: '1050px', margin: '0 auto' }}>
                <div style={{ textAlign: 'center', marginBottom: '2.5rem' }}>
                    <h1 style={{
                        fontSize: '2.5rem', fontWeight: 800,
                        background: 'linear-gradient(90deg, #fff 0%, #0066ff 100%)',
                        WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', marginBottom: '0.75rem',
                    }}>
                        Portfolio Leaderboard
                    </h1>
                    <p style={{ color: 'var(--text-gray)', fontSize: '1rem' }}>
                        Ranked by Time-Weighted Return (TWR). Minimum 5 holdings, no single position &gt; 40%.
                        Click any row to view holdings and transactions.
                    </p>
                </div>

                <div style={{ display: 'flex', gap: '0', marginBottom: '1.75rem', borderBottom: '1px solid var(--border)' }}>
                    {(['all', 'mine'] as LeaderboardScope[]).map(s => (
                        <button key={s} onClick={() => setScope(s)} style={{
                            padding: '0.65rem 1.5rem', border: 'none',
                            borderBottom: scope === s ? '2px solid var(--accent)' : '2px solid transparent',
                            background: 'transparent',
                            color: scope === s ? 'var(--accent)' : 'var(--text-gray)',
                            fontWeight: scope === s ? 700 : 400, cursor: 'pointer',
                            fontSize: '0.95rem', marginBottom: '-1px',
                        }}>
                            {s === 'all' ? 'All Portfolios' : 'My Portfolios'}
                        </button>
                    ))}
                </div>

                <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.75rem' }}>
                    {(Object.keys(RANGE_LABELS) as LeaderboardRange[]).map(r => (
                        <button key={r} onClick={() => setRange(r)} style={{
                            padding: '0.4rem 1rem', borderRadius: '6px', border: '1px solid var(--border)',
                            background: range === r ? 'var(--accent)' : 'var(--bg-card)',
                            color: range === r ? '#fff' : 'var(--text-gray)',
                            fontWeight: range === r ? 700 : 400, cursor: 'pointer', fontSize: '0.85rem',
                        }}>
                            {RANGE_LABELS[r]}
                        </button>
                    ))}
                </div>

                <LeaderboardPane scope={scope} range={range} />

                <p style={{ textAlign: 'center', color: 'var(--text-gray)', fontSize: '0.78rem', marginTop: '1.5rem' }}>
                    TWR (Time-Weighted Return) measures performance independently of cash deposits and withdrawals.
                </p>
            </main>

            <footer className="footer">
                <p>&copy; 2025 SpringHi.ai. All rights reserved.</p>
            </footer>
        </div>
    );
};

export default Leaderboard;
