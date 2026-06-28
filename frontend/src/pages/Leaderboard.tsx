import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getLeaderboard } from '../api/portfolioApi';
import type { LeaderboardEntry } from '../api/portfolioApi';

const isLoggedIn = () => !!localStorage.getItem('token');

type LeaderboardRange = '1W' | '1M' | '3M' | '6M' | '1Y';
type LeaderboardScope = 'mine' | 'all';

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

const EmptyState: React.FC = () => (
    <div style={{
        textAlign: 'center',
        color: 'var(--text-gray)',
        padding: '3rem',
        background: 'var(--bg-card)',
        borderRadius: '12px',
        border: '1px solid var(--border)',
    }}>
        <p style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>No eligible portfolios yet</p>
        <p style={{ fontSize: '0.9rem' }}>Portfolios need at least 5 holdings and 2 daily snapshots to qualify.</p>
    </div>
);

interface LeaderboardTableProps {
    entries: LeaderboardEntry[];
    range: LeaderboardRange;
    showUser: boolean;
}

const LeaderboardTable: React.FC<LeaderboardTableProps> = ({ entries, range, showUser }) => (
    <div style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--border)',
        borderRadius: '12px',
        overflow: 'hidden',
    }}>
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
                        style={{
                            borderBottom: idx < entries.length - 1 ? '1px solid var(--border)' : 'none',
                            background: entry.rank <= 3 ? 'rgba(99,102,241,0.05)' : 'transparent',
                        }}
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
                            <span style={{ fontWeight: 600, color: 'var(--text-light)' }}>
                                {entry.portfolioName}
                            </span>
                        </td>
                        <td style={{
                            padding: '1rem 1.25rem',
                            textAlign: 'right',
                            fontWeight: 700,
                            fontSize: '1.05rem',
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
);

const thStyle: React.CSSProperties = {
    padding: '0.85rem 1.25rem',
    textAlign: 'left',
    fontSize: '0.8rem',
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
    color: 'var(--text-gray)',
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

    return (
        <div className="home-container">
            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav>
                    {isLoggedIn() ? (
                        <>
                            <Link to="/account" className="btn-logout">Account</Link>
                            <Link to="/portfolio" className="btn-primary">My Portfolios</Link>
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
                        fontSize: '2.5rem',
                        fontWeight: 800,
                        background: 'linear-gradient(90deg, #fff 0%, #0066ff 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        marginBottom: '0.75rem',
                    }}>
                        Portfolio Leaderboard
                    </h1>
                    <p style={{ color: 'var(--text-gray)', fontSize: '1rem' }}>
                        Ranked by Time-Weighted Return (TWR). Minimum 5 holdings, no single position &gt; 40%.
                    </p>
                </div>

                <div style={{ display: 'flex', gap: '0', marginBottom: '1.75rem', borderBottom: '1px solid var(--border)' }}>
                    {(['all', 'mine'] as LeaderboardScope[]).map(s => (
                        <button
                            key={s}
                            onClick={() => setScope(s)}
                            style={{
                                padding: '0.65rem 1.5rem',
                                border: 'none',
                                borderBottom: scope === s ? '2px solid var(--accent)' : '2px solid transparent',
                                background: 'transparent',
                                color: scope === s ? 'var(--accent)' : 'var(--text-gray)',
                                fontWeight: scope === s ? 700 : 400,
                                cursor: 'pointer',
                                fontSize: '0.95rem',
                                marginBottom: '-1px',
                            }}
                        >
                            {s === 'all' ? 'All Portfolios' : 'My Portfolios'}
                        </button>
                    ))}
                </div>

                <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.75rem' }}>
                    {(Object.keys(RANGE_LABELS) as LeaderboardRange[]).map(r => (
                        <button
                            key={r}
                            onClick={() => setRange(r)}
                            style={{
                                padding: '0.4rem 1rem',
                                borderRadius: '6px',
                                border: '1px solid var(--border)',
                                background: range === r ? 'var(--accent)' : 'var(--bg-card)',
                                color: range === r ? '#fff' : 'var(--text-gray)',
                                fontWeight: range === r ? 700 : 400,
                                cursor: 'pointer',
                                fontSize: '0.85rem',
                            }}
                        >
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
