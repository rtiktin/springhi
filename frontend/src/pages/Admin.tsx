import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import API_GATEWAY from '../api/apiBase';
import { getLoggedInUsername, isAdmin, startImpersonation } from '../utils/auth';

interface AdminUser {
    id: number;
    username: string;
    email: string;
    firstName: string | null;
    lastName: string | null;
    phone: string | null;
    userType: number;
    userTypeName: string;
    suspendedForChargebacks: boolean;
    adminNotes: string | null;
    createdAt: string;
    lastActiveAt: string | null;
}

interface AdminPortfolio {
    id: number;
    userId: number;
    username: string;
    name: string;
    description: string | null;
    createdAt: string;
}

interface PasswordModal {
    userId: number;
    username: string;
}

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

const TYPE_LABELS: Record<number, string> = {
    10: 'admin',
    8: 'user',
    6: 'closed',
    4: 'suspended',
};

const TYPE_BADGE_COLOR: Record<number, string> = {
    10: '#6c47ff',
    8: '#22c55e',
    6: '#6b7280',
    4: '#ef4444',
    3: '#b91c1c',
};

type AdminTab = 'users' | 'portfolios' | 'stats';

const thStyle: React.CSSProperties = {
    padding: '0.6rem 0.75rem',
    textAlign: 'left',
    color: 'var(--text-gray)',
    fontSize: '0.78rem',
    textTransform: 'uppercase',
};

const tdStyle: React.CSSProperties = {
    padding: '0.65rem 0.75rem',
};

interface DailyCount { date: string; count: number; }
interface StatsData { today: number; thisWeek: number; thisMonth: number; thisYear: number; daily: DailyCount[]; }

const StatCard: React.FC<{ label: string; value: number; color: string }> = ({ label, value, color }) => (
    <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem 1.5rem', minWidth: 120, flex: 1 }}>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-gray)', textTransform: 'uppercase', marginBottom: '0.35rem' }}>{label}</div>
        <div style={{ fontSize: '2rem', fontWeight: 800, color }}>{value}</div>
    </div>
);

const DualBarChart: React.FC<{ userDaily: DailyCount[]; portfolioDaily: DailyCount[] }> = ({ userDaily, portfolioDaily }) => {
    const W = 700, H = 200, PAD_L = 28, PAD_R = 8, PAD_T = 12, PAD_B = 40;
    const innerW = W - PAD_L - PAD_R;
    const innerH = H - PAD_T - PAD_B;
    const n = userDaily.length;
    if (n === 0) return null;

    const maxVal = Math.max(1, ...userDaily.map(d => d.count), ...portfolioDaily.map(d => d.count));
    const groupW = innerW / n;
    const barW = Math.max(2, groupW * 0.35);
    const yTick = (v: number) => PAD_T + innerH - (v / maxVal) * innerH;

    const labelEvery = Math.ceil(n / 8);

    return (
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', display: 'block' }}>
            {[0, 0.25, 0.5, 0.75, 1].map(f => {
                const y = PAD_T + innerH * (1 - f);
                const val = Math.round(maxVal * f);
                return (
                    <g key={f}>
                        <line x1={PAD_L} x2={W - PAD_R} y1={y} y2={y} stroke="var(--border)" strokeWidth={0.5} />
                        <text x={PAD_L - 4} y={y + 4} textAnchor="end" fontSize={9} fill="var(--text-gray)">{val}</text>
                    </g>
                );
            })}
            {userDaily.map((d, i) => {
                const x = PAD_L + i * groupW + groupW / 2;
                const uH = (d.count / maxVal) * innerH;
                const pCount = portfolioDaily[i]?.count ?? 0;
                const pH = (pCount / maxVal) * innerH;
                const lastTick = Math.floor((n - 1) / labelEvery) * labelEvery;
                const showLabel = i % labelEvery === 0 || (i === n - 1 && i - lastTick >= Math.ceil(labelEvery / 2));
                const shortDate = d.date.slice(5);
                return (
                    <g key={d.date}>
                        <rect x={x - barW - 1} y={yTick(d.count)} width={barW} height={uH} fill="#6c47ff" rx={2} opacity={0.85}>
                            <title>{d.date}: {d.count} user(s)</title>
                        </rect>
                        <rect x={x + 1} y={yTick(pCount)} width={barW} height={pH} fill="#22c55e" rx={2} opacity={0.85}>
                            <title>{d.date}: {pCount} portfolio(s)</title>
                        </rect>
                        {showLabel && (
                            <text x={x} y={H - 6} textAnchor="middle" fontSize={9} fill="var(--text-gray)">{shortDate}</text>
                        )}
                    </g>
                );
            })}
            <g>
                <rect x={PAD_L + 4} y={PAD_T} width={10} height={10} fill="#6c47ff" rx={2} />
                <text x={PAD_L + 17} y={PAD_T + 9} fontSize={10} fill="var(--text-gray)">New Users</text>
                <rect x={PAD_L + 84} y={PAD_T} width={10} height={10} fill="#22c55e" rx={2} />
                <text x={PAD_L + 97} y={PAD_T + 9} fontSize={10} fill="var(--text-gray)">New Portfolios</text>
            </g>
        </svg>
    );
};

const StatsPanel: React.FC<{
    userStats: StatsData | null;
    portfolioStats: StatsData | null;
    chartOffset: number;
    setChartOffset: (n: number) => void;
}> = ({ userStats, portfolioStats, chartOffset, setChartOffset }) => {
    if (!userStats || !portfolioStats) return <div style={{ color: 'var(--text-gray)', padding: '2rem', textAlign: 'center' }}>No data yet.</div>;
    const periods: { label: string; key: keyof StatsData }[] = [
        { label: 'Today', key: 'today' },
        { label: 'This Week', key: 'thisWeek' },
        { label: 'This Month', key: 'thisMonth' },
        { label: 'This Year', key: 'thisYear' },
    ];

    const windowLabel = chartOffset === 0
        ? 'Last 30 Days'
        : `${chartOffset + 30} – ${chartOffset + 1} Days Ago`;

    const btnStyle: React.CSSProperties = {
        padding: '0.3rem 0.8rem',
        borderRadius: 6,
        border: '1px solid var(--border)',
        background: 'var(--bg-dark)',
        color: 'var(--text-primary)',
        cursor: 'pointer',
        fontSize: '0.85rem',
    };

    return (
        <div>
            <h3 style={{ marginBottom: '1.25rem', color: 'var(--text-primary)', fontWeight: 700 }}>New Users</h3>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
                {periods.map(p => (
                    <StatCard key={p.key} label={p.label} value={userStats[p.key] as number} color="#6c47ff" />
                ))}
            </div>

            <h3 style={{ marginBottom: '1.25rem', color: 'var(--text-primary)', fontWeight: 700 }}>New Portfolios</h3>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
                {periods.map(p => (
                    <StatCard key={p.key} label={p.label} value={portfolioStats[p.key] as number} color="#22c55e" />
                ))}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
                <h3 style={{ margin: 0, color: 'var(--text-primary)', fontWeight: 700 }}>{windowLabel}</h3>
                <button style={btnStyle} onClick={() => setChartOffset(chartOffset + 30)}>&#8592; Back</button>
                <button style={{ ...btnStyle, opacity: chartOffset === 0 ? 0.4 : 1, pointerEvents: chartOffset === 0 ? 'none' : 'auto' }}
                    onClick={() => setChartOffset(Math.max(0, chartOffset - 30))}>Forward &#8594;</button>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-gray)', marginLeft: '0.5rem' }}>
                    <span style={{ color: '#6c47ff', fontWeight: 700 }}>{userStats.daily.reduce((s, d) => s + d.count, 0)}</span> new users&nbsp;&nbsp;
                    <span style={{ color: '#22c55e', fontWeight: 700 }}>{portfolioStats.daily.reduce((s, d) => s + d.count, 0)}</span> new portfolios
                </span>
            </div>
            <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem' }}>
                <DualBarChart userDaily={userStats.daily} portfolioDaily={portfolioStats.daily} />
            </div>
        </div>
    );
};

const Admin: React.FC = () => {
    const navigate = useNavigate();
    const username = getLoggedInUsername();
    const [tab, setTab] = useState<AdminTab>('users');
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [portfolios, setPortfolios] = useState<AdminPortfolio[]>([]);
    const [loadingUsers, setLoadingUsers] = useState(false);
    const [loadingPortfolios, setLoadingPortfolios] = useState(false);
    const [error, setError] = useState('');
    const [changingType, setChangingType] = useState<number | null>(null);
    const [impersonating, setImpersonating] = useState<number | null>(null);

    const [pwModal, setPwModal] = useState<PasswordModal | null>(null);
    const [newPassword, setNewPassword] = useState('');
    const [pwError, setPwError] = useState('');
    const [pwSaving, setPwSaving] = useState(false);
    const [pwSuccess, setPwSuccess] = useState('');

    const [emailModal, setEmailModal] = useState<PasswordModal | null>(null);
    const [newEmail, setNewEmail] = useState('');
    const [emailError, setEmailError] = useState('');
    const [emailSaving, setEmailSaving] = useState(false);
    const [emailSuccess, setEmailSuccess] = useState('');

    const [notesModal, setNotesModal] = useState<PasswordModal | null>(null);
    const [notesText, setNotesText] = useState('');
    const [notesError, setNotesError] = useState('');
    const [notesSaving, setNotesSaving] = useState(false);
    const [notesSuccess, setNotesSuccess] = useState('');

    const [userStats, setUserStats] = useState<StatsData | null>(null);
    const [portfolioStats, setPortfolioStats] = useState<StatsData | null>(null);
    const [loadingStats, setLoadingStats] = useState(false);
    const [chartOffset, setChartOffset] = useState(0);

    useEffect(() => {
        if (!isAdmin()) {
            navigate('/portfolio');
        }
    }, [navigate]);

    useEffect(() => {
        if (tab === 'users') {
            loadUsers();
        } else if (tab === 'portfolios') {
            loadPortfolios();
        } else if (tab === 'stats') {
            loadStats(chartOffset);
        }
    }, [tab]);

    useEffect(() => {
        if (tab === 'stats') {
            loadStats(chartOffset);
        }
    }, [chartOffset]);

    const loadStats = (offset: number) => {
        setLoadingStats(true);
        Promise.all([
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/users?daysOffset=${offset}`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/portfolios?daysOffset=${offset}`, { headers: authHeader() }),
        ]).then(([uRes, pRes]) => {
            setUserStats(uRes.data);
            setPortfolioStats(pRes.data);
        }).catch(() => setError('Failed to load statistics.'))
          .finally(() => setLoadingStats(false));
    };

    const loadUsers = () => {
        setLoadingUsers(true);
        setError('');
        axios.get(`${API_GATEWAY}/api/v1/admin/users`, { headers: authHeader() })
            .then(res => setUsers(res.data))
            .catch(() => setError('Failed to load users.'))
            .finally(() => setLoadingUsers(false));
    };

    const loadPortfolios = () => {
        setLoadingPortfolios(true);
        setError('');
        axios.get(`${API_GATEWAY}/api/v1/admin/portfolios`, { headers: authHeader() })
            .then(res => setPortfolios(res.data))
            .catch(() => setError('Failed to load portfolios.'))
            .finally(() => setLoadingPortfolios(false));
    };

    const handleTypeChange = (userId: number, value: string) => {
        setChangingType(userId);
        if (value === 'chargeback') {
            axios.put(`${API_GATEWAY}/api/v1/admin/users/${userId}/suspend-chargebacks`, {}, { headers: authHeader() })
                .then(res => {
                    setUsers(prev => prev.map(u => u.id === userId ? { ...u, userType: res.data.userType, userTypeName: res.data.userTypeName, suspendedForChargebacks: res.data.suspendedForChargebacks } : u));
                })
                .catch(() => setError('Failed to update user type.'))
                .finally(() => setChangingType(null));
        } else {
            const newType = parseInt(value);
            axios.put(`${API_GATEWAY}/api/v1/admin/users/${userId}/type`, { userType: newType }, { headers: authHeader() })
                .then(res => {
                    setUsers(prev => prev.map(u => u.id === userId ? { ...u, userType: res.data.userType, userTypeName: res.data.userTypeName, suspendedForChargebacks: res.data.suspendedForChargebacks } : u));
                })
                .catch(() => setError('Failed to update user type.'))
                .finally(() => setChangingType(null));
        }
    };

    const handleChangePassword = () => {
        if (!pwModal) return;
        if (newPassword.length < 8) {
            setPwError('Password must be at least 8 characters.');
            return;
        }
        setPwSaving(true);
        setPwError('');
        axios.put(`${API_GATEWAY}/api/v1/admin/users/${pwModal.userId}/password`, { password: newPassword }, { headers: authHeader() })
            .then(() => {
                setPwSuccess(`Password updated for ${pwModal.username}.`);
                setNewPassword('');
                setTimeout(() => {
                    setPwModal(null);
                    setPwSuccess('');
                }, 1500);
            })
            .catch(err => setPwError(err?.response?.data?.message ?? 'Failed to update password.'))
            .finally(() => setPwSaving(false));
    };

    const handleChangeEmail = () => {
        if (!emailModal) return;
        if (!newEmail.trim()) {
            setEmailError('Email address is required.');
            return;
        }
        setEmailSaving(true);
        setEmailError('');
        axios.put(`${API_GATEWAY}/api/v1/admin/users/${emailModal.userId}/email`, { email: newEmail.trim() }, { headers: authHeader() })
            .then(res => {
                setUsers(prev => prev.map(u => u.id === emailModal.userId ? { ...u, email: res.data.email } : u));
                setEmailSuccess(`Email updated for ${emailModal.username}.`);
                setNewEmail('');
                setTimeout(() => {
                    setEmailModal(null);
                    setEmailSuccess('');
                }, 1500);
            })
            .catch(err => setEmailError(err?.response?.data?.message ?? 'Failed to update email.'))
            .finally(() => setEmailSaving(false));
    };

    const handleSaveNotes = () => {
        if (!notesModal) return;
        setNotesSaving(true);
        setNotesError('');
        axios.put(`${API_GATEWAY}/api/v1/admin/users/${notesModal.userId}/notes`, { notes: notesText }, { headers: authHeader() })
            .then(res => {
                setUsers(prev => prev.map(u => u.id === notesModal.userId ? { ...u, adminNotes: res.data.adminNotes } : u));
                setNotesSuccess('Notes saved.');
                setTimeout(() => {
                    setNotesModal(null);
                    setNotesSuccess('');
                }, 1500);
            })
            .catch(err => setNotesError(err?.response?.data?.message ?? 'Failed to save notes.'))
            .finally(() => setNotesSaving(false));
    };

    const handleImpersonate = (user: AdminUser) => {
        setImpersonating(user.id);
        setError('');
        axios.post(`${API_GATEWAY}/api/v1/admin/users/${user.id}/impersonate`, {}, { headers: authHeader() })
            .then(res => {
                startImpersonation(res.data.token);
                navigate('/portfolio');
            })
            .catch(() => setError(`Failed to impersonate ${user.username}.`))
            .finally(() => setImpersonating(null));
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    const tabStyle = (t: AdminTab): React.CSSProperties => ({
        padding: '0.5rem 1.25rem',
        borderRadius: '8px 8px 0 0',
        border: 'none',
        cursor: 'pointer',
        fontWeight: 600,
        fontSize: '0.95rem',
        background: tab === t ? 'var(--bg-card)' : 'transparent',
        color: tab === t ? 'var(--text-primary)' : 'var(--text-gray)',
        borderBottom: tab === t ? '2px solid #6c47ff' : '2px solid transparent',
    });

    return (
        <div className="portfolio-page">
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/portfolio" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/portfolio" className="btn-logout">Portfolios</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    <Link to="/account" className="btn-logout">Account</Link>
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <h1 className="portfolio-heading">Admin Panel</h1>
                <p className="portfolio-sub">Manage users and portfolios.</p>

                {error && (
                    <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '1rem' }}>
                        {error}
                    </div>
                )}

                <div style={{ display: 'flex', gap: '0.25rem', marginBottom: 0, borderBottom: '1px solid var(--border)' }}>
                    <button style={tabStyle('users')} onClick={() => setTab('users')}>Users</button>
                    <button style={tabStyle('portfolios')} onClick={() => setTab('portfolios')}>Portfolios</button>
                    <button style={tabStyle('stats')} onClick={() => setTab('stats')}>Statistics</button>
                </div>

                <div style={{ background: 'var(--bg-card)', borderRadius: '0 8px 8px 8px', border: '1px solid var(--border)', borderTop: 'none', padding: '1.5rem' }}>
                    {tab === 'users' && (
                        <>
                            {loadingUsers ? (
                                <div className="portfolio-loading">Loading users…</div>
                            ) : (
                                <div style={{ overflowX: 'auto' }}>
                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                                        <thead>
                                            <tr style={{ borderBottom: '1px solid var(--border)' }}>
                                                <th style={thStyle}>Username</th>
                                                <th style={thStyle}>Email</th>
                                                <th style={thStyle}>Name</th>
                                                <th style={thStyle}>Phone</th>
                                                <th style={thStyle}>Member Since</th>
                                                <th style={thStyle}>Last Active</th>
                                                <th style={thStyle}>Type</th>
                                                <th style={thStyle}>Change Type</th>
                                                <th style={thStyle}>Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {users.map(u => (
                                                <tr key={u.id} style={{ borderBottom: '1px solid var(--border)' }}>
                                                    <td style={{ ...tdStyle, fontWeight: 600 }}>{u.username}</td>
                                                    <td style={tdStyle}>{u.email}</td>
                                                    <td style={tdStyle}>{[u.firstName, u.lastName].filter(Boolean).join(' ') || '—'}</td>
                                                    <td style={tdStyle}>{u.phone || '—'}</td>
                                                    <td style={tdStyle}>{u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}</td>
                                                    <td style={tdStyle}>{u.lastActiveAt ? new Date(u.lastActiveAt).toLocaleDateString() : '—'}</td>
                                                    <td style={tdStyle}>
                                                        <span style={{
                                                            background: u.userType === 4 && u.suspendedForChargebacks ? '#b91c1c' : (TYPE_BADGE_COLOR[u.userType] ?? '#6b7280'),
                                                            color: '#fff',
                                                            borderRadius: 6,
                                                            padding: '0.2rem 0.6rem',
                                                            fontSize: '0.78rem',
                                                            fontWeight: 600,
                                                        }}>
                                                            {u.userTypeName}
                                                        </span>
                                                    </td>
                                                    <td style={tdStyle}>
                                                        <select
                                                            value={u.userType === 4 && u.suspendedForChargebacks ? 'chargeback' : String(u.userType)}
                                                            disabled={changingType === u.id}
                                                            onChange={e => handleTypeChange(u.id, e.target.value)}
                                                            style={{
                                                                background: 'var(--bg-input, #1e2035)',
                                                                color: 'var(--text-primary)',
                                                                border: '1px solid var(--border)',
                                                                borderRadius: 6,
                                                                padding: '0.3rem 0.5rem',
                                                                fontSize: '0.85rem',
                                                                cursor: 'pointer',
                                                            }}
                                                        >
                                                            {Object.entries(TYPE_LABELS).map(([val, label]) => (
                                                                <option key={val} value={val}>{label}</option>
                                                            ))}
                                                            <option value="chargeback">suspended for chargebacks</option>
                                                        </select>
                                                    </td>
                                                    <td style={{ ...tdStyle, display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                                        <button
                                                            onClick={() => { setNotesModal({ userId: u.id, username: u.username }); setNotesText(u.adminNotes ?? ''); setNotesError(''); setNotesSuccess(''); }}
                                                            style={{
                                                                background: '#1d4ed8',
                                                                color: '#fff',
                                                                border: 'none',
                                                                borderRadius: 6,
                                                                padding: '0.3rem 0.65rem',
                                                                fontSize: '0.82rem',
                                                                cursor: 'pointer',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                            title={u.adminNotes ? 'Has notes' : 'Add notes'}
                                                        >
                                                            {u.adminNotes ? '📝 Notes' : 'Notes'}
                                                        </button>
                                                        <button
                                                            onClick={() => { setEmailModal({ userId: u.id, username: u.username }); setNewEmail(u.email); setEmailError(''); setEmailSuccess(''); }}
                                                            style={{
                                                                background: '#374151',
                                                                color: '#fff',
                                                                border: 'none',
                                                                borderRadius: 6,
                                                                padding: '0.3rem 0.65rem',
                                                                fontSize: '0.82rem',
                                                                cursor: 'pointer',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            Change Email
                                                        </button>
                                                        <button
                                                            onClick={() => { setPwModal({ userId: u.id, username: u.username }); setNewPassword(''); setPwError(''); setPwSuccess(''); }}
                                                            style={{
                                                                background: '#374151',
                                                                color: '#fff',
                                                                border: 'none',
                                                                borderRadius: 6,
                                                                padding: '0.3rem 0.65rem',
                                                                fontSize: '0.82rem',
                                                                cursor: 'pointer',
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            Change Password
                                                        </button>
                                                        <button
                                                            onClick={() => handleImpersonate(u)}
                                                            disabled={impersonating === u.id || u.userType === 10}
                                                            title={u.userType === 10 ? 'Cannot impersonate another admin' : ''}
                                                            style={{
                                                                background: u.userType === 10 ? '#374151' : '#6c47ff',
                                                                color: '#fff',
                                                                border: 'none',
                                                                borderRadius: 6,
                                                                padding: '0.3rem 0.65rem',
                                                                fontSize: '0.82rem',
                                                                cursor: u.userType === 10 ? 'not-allowed' : 'pointer',
                                                                opacity: u.userType === 10 ? 0.5 : 1,
                                                                whiteSpace: 'nowrap',
                                                            }}
                                                        >
                                                            {impersonating === u.id ? 'Switching…' : 'Become User'}
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))}
                                            {users.length === 0 && (
                                                <tr>
                                                    <td colSpan={9} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-gray)' }}>No users found.</td>
                                                </tr>
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </>
                    )}

                    {tab === 'portfolios' && (
                        <>
                            {loadingPortfolios ? (
                                <div className="portfolio-loading">Loading portfolios…</div>
                            ) : (
                                <div style={{ overflowX: 'auto' }}>
                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                                        <thead>
                                            <tr style={{ borderBottom: '1px solid var(--border)' }}>
                                                <th style={thStyle}>Username</th>
                                                <th style={thStyle}>Portfolio Name</th>
                                                <th style={thStyle}>Description</th>
                                                <th style={thStyle}>Created</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {portfolios.map(p => (
                                                <tr key={p.id} style={{ borderBottom: '1px solid var(--border)' }}>
                                                    <td style={{ ...tdStyle, fontWeight: 600 }}>{p.username}</td>
                                                    <td style={tdStyle}>{p.name}</td>
                                                    <td style={{ ...tdStyle, color: 'var(--text-gray)' }}>{p.description || '—'}</td>
                                                    <td style={tdStyle}>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '—'}</td>
                                                </tr>
                                            ))}
                                            {portfolios.length === 0 && (
                                                <tr>
                                                    <td colSpan={4} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-gray)' }}>No portfolios found.</td>
                                                </tr>
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </>
                    )}

                    {tab === 'stats' && (
                        <>
                            {loadingStats ? (
                                <div className="portfolio-loading">Loading statistics…</div>
                            ) : (
                                <StatsPanel userStats={userStats} portfolioStats={portfolioStats} chartOffset={chartOffset} setChartOffset={setChartOffset} />
                            )}
                        </>
                    )}
                </div>
            </main>

            {emailModal && (
                <div className="modal-overlay" onClick={() => setEmailModal(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 420, width: '95%' }}>
                        <div className="modal-header">
                            <h2 className="modal-title">Change Email</h2>
                            <button className="modal-close" onClick={() => setEmailModal(null)}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem' }}>
                            <p style={{ marginBottom: '1rem', color: 'var(--text-gray)' }}>
                                Update email address for <strong style={{ color: 'var(--text-primary)' }}>{emailModal.username}</strong>.
                            </p>
                            {emailSuccess && (
                                <div style={{ background: '#d1fae5', color: '#065f46', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {emailSuccess}
                                </div>
                            )}
                            {emailError && (
                                <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {emailError}
                                </div>
                            )}
                            <label className="form-label">New Email Address</label>
                            <input
                                type="email"
                                className="profile-input"
                                value={newEmail}
                                onChange={e => { setNewEmail(e.target.value); setEmailError(''); }}
                                placeholder="user@example.com"
                                style={{ marginBottom: '1rem', width: '100%' }}
                                autoFocus
                            />
                            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                                <button className="btn-logout" onClick={() => setEmailModal(null)}>Cancel</button>
                                <button className="btn-trade" onClick={handleChangeEmail} disabled={emailSaving}>
                                    {emailSaving ? 'Saving…' : 'Update Email'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {pwModal && (
                <div className="modal-overlay" onClick={() => setPwModal(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 420, width: '95%' }}>
                        <div className="modal-header">
                            <h2 className="modal-title">Change Password</h2>
                            <button className="modal-close" onClick={() => setPwModal(null)}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem' }}>
                            <p style={{ marginBottom: '1rem', color: 'var(--text-gray)' }}>
                                Set a new password for <strong style={{ color: 'var(--text-primary)' }}>{pwModal.username}</strong>.
                            </p>
                            {pwSuccess && (
                                <div style={{ background: '#d1fae5', color: '#065f46', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {pwSuccess}
                                </div>
                            )}
                            {pwError && (
                                <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {pwError}
                                </div>
                            )}
                            <label className="form-label">New Password</label>
                            <input
                                type="password"
                                className="profile-input"
                                value={newPassword}
                                onChange={e => { setNewPassword(e.target.value); setPwError(''); }}
                                placeholder="At least 8 characters"
                                style={{ marginBottom: '1rem', width: '100%' }}
                                autoFocus
                            />
                            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                                <button className="btn-logout" onClick={() => setPwModal(null)}>Cancel</button>
                                <button className="btn-trade" onClick={handleChangePassword} disabled={pwSaving}>
                                    {pwSaving ? 'Saving…' : 'Update Password'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
            {notesModal && (
                <div className="modal-overlay" onClick={() => setNotesModal(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 500, width: '95%' }}>
                        <div className="modal-header">
                            <h2 className="modal-title">Admin Notes</h2>
                            <button className="modal-close" onClick={() => setNotesModal(null)}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem' }}>
                            <p style={{ marginBottom: '1rem', color: 'var(--text-gray)' }}>
                                Notes for <strong style={{ color: 'var(--text-primary)' }}>{notesModal.username}</strong>
                            </p>
                            {notesSuccess && (
                                <div style={{ background: '#d1fae5', color: '#065f46', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {notesSuccess}
                                </div>
                            )}
                            {notesError && (
                                <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 6, padding: '0.6rem 0.75rem', marginBottom: '0.75rem', fontSize: '0.9rem' }}>
                                    {notesError}
                                </div>
                            )}
                            <textarea
                                className="profile-input"
                                value={notesText}
                                onChange={e => { setNotesText(e.target.value); setNotesError(''); }}
                                placeholder="Enter admin notes here…"
                                rows={6}
                                style={{ marginBottom: '1rem', width: '100%', resize: 'vertical', fontFamily: 'inherit' }}
                                autoFocus
                            />
                            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                                <button className="btn-logout" onClick={() => setNotesModal(null)}>Cancel</button>
                                <button className="btn-trade" onClick={handleSaveNotes} disabled={notesSaving}>
                                    {notesSaving ? 'Saving…' : 'Save Notes'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Admin;
