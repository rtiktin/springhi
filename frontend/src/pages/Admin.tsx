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
    planName: string;
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

type AdminTab = 'users' | 'portfolios' | 'stats' | 'config';

interface SubscriptionPlan {
    id: number;
    planName: string;
    displayName: string;
    monthlyPrice: number;
    annualPrice: number;
    maxPortfolios: number;
    maxOptimizationsPerMonth: number;
    description: string;
}

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
interface SubscriptionStats { totalUsers: number; free: number; basic: number; premium: number; }
interface SubscriptionDailyStats { basicDaily: DailyCount[]; premiumDaily: DailyCount[]; }

const StatCard: React.FC<{ label: string; value: number; color: string }> = ({ label, value, color }) => (
    <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem 1.5rem', minWidth: 120, flex: 1 }}>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-gray)', textTransform: 'uppercase', marginBottom: '0.35rem' }}>{label}</div>
        <div style={{ fontSize: '2rem', fontWeight: 800, color }}>{value}</div>
    </div>
);

const niceTicks = (dataMax: number): { ticks: number[]; chartMax: number } => {
    if (dataMax === 0) return { ticks: [0, 1, 2, 3, 4], chartMax: 4 };
    if (dataMax <= 4) {
        const ticks = Array.from({ length: dataMax + 1 }, (_, i) => i);
        return { ticks, chartMax: dataMax };
    }
    const step = Math.ceil(dataMax / 4);
    const chartMax = step * 4;
    return { ticks: [0, step, step * 2, step * 3, chartMax], chartMax };
};

const UsersChart: React.FC<{
    userDaily: DailyCount[];
    basicDaily: DailyCount[];
    premiumDaily: DailyCount[];
}> = ({ userDaily, basicDaily, premiumDaily }) => {
    const W = 700, H = 200, PAD_L = 28, PAD_R = 8, PAD_T = 20, PAD_B = 40;
    const innerW = W - PAD_L - PAD_R;
    const innerH = H - PAD_T - PAD_B;
    const n = userDaily.length;
    if (n === 0) return null;

    const dataMax = Math.max(0,
        ...userDaily.map(d => d.count),
        ...basicDaily.map(d => d.count),
        ...premiumDaily.map(d => d.count),
    );
    const { ticks, chartMax } = niceTicks(dataMax);
    const groupW = innerW / n;
    const barW = Math.max(1, groupW * 0.25);
    const yPos = (v: number) => PAD_T + innerH - (v / chartMax) * innerH;
    const labelEvery = Math.ceil(n / 8);

    return (
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', display: 'block' }}>
            {ticks.map(val => {
                const y = yPos(val);
                return (
                    <g key={val}>
                        <line x1={PAD_L} x2={W - PAD_R} y1={y} y2={y} stroke="var(--border)" strokeWidth={0.5} />
                        <text x={PAD_L - 4} y={y + 4} textAnchor="end" fontSize={9} fill="var(--text-gray)">{val}</text>
                    </g>
                );
            })}
            {userDaily.map((d, i) => {
                const cx = PAD_L + i * groupW + groupW / 2;
                const uCount = d.count;
                const bCount = basicDaily[i]?.count ?? 0;
                const pCount = premiumDaily[i]?.count ?? 0;
                const lastTick = Math.floor((n - 1) / labelEvery) * labelEvery;
                const showLabel = i % labelEvery === 0 || (i === n - 1 && i - lastTick >= Math.ceil(labelEvery / 2));
                return (
                    <g key={d.date}>
                        <rect x={cx - barW * 1.5 - 1} y={yPos(uCount)} width={barW} height={(uCount / chartMax) * innerH} fill="#6c47ff" rx={1} opacity={0.85}>
                            <title>{d.date}: {uCount} new user(s)</title>
                        </rect>
                        <rect x={cx - barW * 0.5} y={yPos(bCount)} width={barW} height={(bCount / chartMax) * innerH} fill="#f59e0b" rx={1} opacity={0.85}>
                            <title>{d.date}: {bCount} new Basic subscription(s)</title>
                        </rect>
                        <rect x={cx + barW * 0.5 + 1} y={yPos(pCount)} width={barW} height={(pCount / chartMax) * innerH} fill="#a855f7" rx={1} opacity={0.85}>
                            <title>{d.date}: {pCount} new Premium subscription(s)</title>
                        </rect>
                        {showLabel && (
                            <text x={cx} y={H - 6} textAnchor="middle" fontSize={9} fill="var(--text-gray)">{d.date.slice(5)}</text>
                        )}
                    </g>
                );
            })}
            <g>
                <rect x={PAD_L + 4} y={4} width={10} height={10} fill="#6c47ff" rx={2} />
                <text x={PAD_L + 17} y={13} fontSize={10} fill="var(--text-gray)">New Users</text>
                <rect x={PAD_L + 88} y={4} width={10} height={10} fill="#f59e0b" rx={2} />
                <text x={PAD_L + 101} y={13} fontSize={10} fill="var(--text-gray)">New Basic</text>
                <rect x={PAD_L + 170} y={4} width={10} height={10} fill="#a855f7" rx={2} />
                <text x={PAD_L + 183} y={13} fontSize={10} fill="var(--text-gray)">New Premium</text>
            </g>
        </svg>
    );
};

const PortfoliosChart: React.FC<{ portfolioDaily: DailyCount[] }> = ({ portfolioDaily }) => {
    const W = 700, H = 200, PAD_L = 28, PAD_R = 8, PAD_T = 12, PAD_B = 40;
    const innerW = W - PAD_L - PAD_R;
    const innerH = H - PAD_T - PAD_B;
    const n = portfolioDaily.length;
    if (n === 0) return null;

    const dataMax = Math.max(0, ...portfolioDaily.map(d => d.count));
    const { ticks, chartMax } = niceTicks(dataMax);
    const groupW = innerW / n;
    const barW = Math.max(2, groupW * 0.5);
    const yPos = (v: number) => PAD_T + innerH - (v / chartMax) * innerH;
    const labelEvery = Math.ceil(n / 8);

    return (
        <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', display: 'block' }}>
            {ticks.map(val => {
                const y = yPos(val);
                return (
                    <g key={val}>
                        <line x1={PAD_L} x2={W - PAD_R} y1={y} y2={y} stroke="var(--border)" strokeWidth={0.5} />
                        <text x={PAD_L - 4} y={y + 4} textAnchor="end" fontSize={9} fill="var(--text-gray)">{val}</text>
                    </g>
                );
            })}
            {portfolioDaily.map((d, i) => {
                const cx = PAD_L + i * groupW + groupW / 2;
                const lastTick = Math.floor((n - 1) / labelEvery) * labelEvery;
                const showLabel = i % labelEvery === 0 || (i === n - 1 && i - lastTick >= Math.ceil(labelEvery / 2));
                return (
                    <g key={d.date}>
                        <rect x={cx - barW / 2} y={yPos(d.count)} width={barW} height={(d.count / chartMax) * innerH} fill="#22c55e" rx={2} opacity={0.85}>
                            <title>{d.date}: {d.count} portfolio(s)</title>
                        </rect>
                        {showLabel && (
                            <text x={cx} y={H - 6} textAnchor="middle" fontSize={9} fill="var(--text-gray)">{d.date.slice(5)}</text>
                        )}
                    </g>
                );
            })}
            <g>
                <rect x={PAD_L + 4} y={PAD_T} width={10} height={10} fill="#22c55e" rx={2} />
                <text x={PAD_L + 17} y={PAD_T + 9} fontSize={10} fill="var(--text-gray)">New Portfolios</text>
            </g>
        </svg>
    );
};

const StatsPanel: React.FC<{
    userStats: StatsData | null;
    portfolioStats: StatsData | null;
    subscriptionStats: SubscriptionStats | null;
    subscriptionDailyStats: SubscriptionDailyStats | null;
    chartOffset: number;
    setChartOffset: (n: number) => void;
    portfolioChartOffset: number;
    setPortfolioChartOffset: (n: number) => void;
}> = ({ userStats, portfolioStats, subscriptionStats, subscriptionDailyStats, chartOffset, setChartOffset, portfolioChartOffset, setPortfolioChartOffset }) => {
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

    const portfolioWindowLabel = portfolioChartOffset === 0
        ? 'Last 30 Days'
        : `${portfolioChartOffset + 30} – ${portfolioChartOffset + 1} Days Ago`;

    const btnStyle: React.CSSProperties = {
        padding: '0.3rem 0.8rem',
        borderRadius: 6,
        border: '1px solid var(--border)',
        background: 'var(--bg-dark)',
        color: 'var(--text-primary)',
        cursor: 'pointer',
        fontSize: '0.85rem',
    };

    const emptyDaily = userStats.daily.map(d => ({ date: d.date, count: 0 }));

    return (
        <div>
            <h3 style={{ marginBottom: '1.25rem', color: 'var(--text-primary)', fontWeight: 700 }}>New Users</h3>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
                {periods.map(p => (
                    <StatCard key={p.key} label={p.label} value={userStats[p.key] as number} color="#6c47ff" />
                ))}
            </div>

            {subscriptionStats && (
                <>
                    <h3 style={{ marginBottom: '1.25rem', color: 'var(--text-primary)', fontWeight: 700 }}>Subscriptions</h3>
                    <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
                        <StatCard label="Total Users" value={subscriptionStats.totalUsers} color="var(--text-primary)" />
                        <StatCard label="Free" value={subscriptionStats.free} color="#6b7280" />
                        <StatCard label="Basic" value={subscriptionStats.basic} color="#f59e0b" />
                        <StatCard label="Premium" value={subscriptionStats.premium} color="#a855f7" />
                    </div>
                    <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '2rem', fontSize: '0.85rem', color: 'var(--text-gray)' }}>
                        {subscriptionStats.totalUsers > 0 && (
                            <>
                                <span style={{ color: '#6b7280', fontWeight: 700 }}>{Math.round(subscriptionStats.free / subscriptionStats.totalUsers * 100)}%</span> Free&nbsp;&nbsp;
                                <span style={{ color: '#f59e0b', fontWeight: 700 }}>{Math.round(subscriptionStats.basic / subscriptionStats.totalUsers * 100)}%</span> Basic&nbsp;&nbsp;
                                <span style={{ color: '#a855f7', fontWeight: 700 }}>{Math.round(subscriptionStats.premium / subscriptionStats.totalUsers * 100)}%</span> Premium
                            </>
                        )}
                    </div>
                </>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
                <h3 style={{ margin: 0, color: 'var(--text-primary)', fontWeight: 700 }}>{windowLabel} — Users &amp; Subscriptions</h3>
                <button style={btnStyle} onClick={() => setChartOffset(chartOffset + 30)}>&#8592; Back</button>
                <button style={{ ...btnStyle, opacity: chartOffset === 0 ? 0.4 : 1, pointerEvents: chartOffset === 0 ? 'none' : 'auto' }}
                    onClick={() => setChartOffset(Math.max(0, chartOffset - 30))}>Forward &#8594;</button>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-gray)', marginLeft: '0.5rem' }}>
                    <span style={{ color: '#6c47ff', fontWeight: 700 }}>{userStats.daily.reduce((s, d) => s + d.count, 0)}</span> new users
                </span>
            </div>
            <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem', marginBottom: '2rem' }}>
                <UsersChart
                    userDaily={userStats.daily}
                    basicDaily={subscriptionDailyStats?.basicDaily ?? emptyDaily}
                    premiumDaily={subscriptionDailyStats?.premiumDaily ?? emptyDaily}
                />
            </div>

            <h3 style={{ marginBottom: '1.25rem', color: 'var(--text-primary)', fontWeight: 700 }}>New Portfolios</h3>
            <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
                {periods.map(p => (
                    <StatCard key={p.key} label={p.label} value={portfolioStats[p.key] as number} color="#22c55e" />
                ))}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
                <h3 style={{ margin: 0, color: 'var(--text-primary)', fontWeight: 700 }}>{portfolioWindowLabel} — Portfolios</h3>
                <button style={btnStyle} onClick={() => setPortfolioChartOffset(portfolioChartOffset + 30)}>&#8592; Back</button>
                <button style={{ ...btnStyle, opacity: portfolioChartOffset === 0 ? 0.4 : 1, pointerEvents: portfolioChartOffset === 0 ? 'none' : 'auto' }}
                    onClick={() => setPortfolioChartOffset(Math.max(0, portfolioChartOffset - 30))}>Forward &#8594;</button>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-gray)', marginLeft: '0.5rem' }}>
                    <span style={{ color: '#22c55e', fontWeight: 700 }}>{portfolioStats.daily.reduce((s, d) => s + d.count, 0)}</span> new portfolios
                </span>
            </div>
            <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem' }}>
                <PortfoliosChart portfolioDaily={portfolioStats.daily} />
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

    type UserSortKey = 'username' | 'email' | 'name' | 'phone' | 'createdAt' | 'lastActiveAt' | 'planName' | 'userTypeName';
    const DATE_COLS: UserSortKey[] = ['createdAt', 'lastActiveAt'];
    const [userSort, setUserSort] = useState<{ key: UserSortKey; dir: 'asc' | 'desc' }>({ key: 'createdAt', dir: 'desc' });

    const handleUserSort = (key: UserSortKey) => {
        setUserSort(prev => {
            if (prev.key === key) return { key, dir: prev.dir === 'asc' ? 'desc' : 'asc' };
            const defaultDir = DATE_COLS.includes(key) ? 'desc' : 'asc';
            return { key, dir: defaultDir };
        });
    };

    const [userFilters, setUserFilters] = useState({ username: '', name: '', email: '', plan: '', type: '' });
    const setFilter = (field: keyof typeof userFilters, value: string) =>
        setUserFilters(prev => ({ ...prev, [field]: value }));

    const filteredUsers = users.filter(u => {
        const fullName = [u.firstName, u.lastName].filter(Boolean).join(' ').toLowerCase();
        if (userFilters.username.length >= 2 && !u.username.toLowerCase().includes(userFilters.username.toLowerCase())) return false;
        if (userFilters.name.length >= 2 && !fullName.includes(userFilters.name.toLowerCase())) return false;
        if (userFilters.email && !u.email.toLowerCase().includes(userFilters.email.toLowerCase())) return false;
        if (userFilters.plan && (u.planName ?? 'FREE').toUpperCase() !== userFilters.plan) return false;
        if (userFilters.type) {
            if (userFilters.type === 'chargeback') {
                if (!(u.userType === 4 && u.suspendedForChargebacks)) return false;
            } else if (String(u.userType) !== userFilters.type) return false;
        }
        return true;
    });

    const sortedUsers = [...filteredUsers].sort((a, b) => {
        const { key, dir } = userSort;
        let av: string, bv: string;
        if (key === 'name') {
            av = [a.firstName, a.lastName].filter(Boolean).join(' ').toLowerCase();
            bv = [b.firstName, b.lastName].filter(Boolean).join(' ').toLowerCase();
        } else if (key === 'createdAt' || key === 'lastActiveAt') {
            av = a[key] ?? '';
            bv = b[key] ?? '';
        } else {
            av = ((a[key] as string | null) ?? '').toLowerCase();
            bv = ((b[key] as string | null) ?? '').toLowerCase();
        }
        if (av < bv) return dir === 'asc' ? -1 : 1;
        if (av > bv) return dir === 'asc' ? 1 : -1;
        return 0;
    });

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
    const [subscriptionStats, setSubscriptionStats] = useState<SubscriptionStats | null>(null);
    const [subscriptionDailyStats, setSubscriptionDailyStats] = useState<SubscriptionDailyStats | null>(null);
    const [loadingStats, setLoadingStats] = useState(false);
    const [chartOffset, setChartOffset] = useState(0);
    const [portfolioChartOffset, setPortfolioChartOffset] = useState(0);

    const [ipModal, setIpModal] = useState<{ userId: number; username: string } | null>(null);
    const [ipAddresses, setIpAddresses] = useState<{ ipAddress: string; firstSeen: string; lastSeen: string; requestCount: number }[]>([]);
    const [ipLoading, setIpLoading] = useState(false);

    const [linkedModal, setLinkedModal] = useState<{ userId: number; username: string } | null>(null);
    const [linkedAccounts, setLinkedAccounts] = useState<{ userId: number; username: string; email: string | null; sharedValues: string[] }[]>([]);
    const [linkedLoading, setLinkedLoading] = useState(false);

    const [subscriptionPlans, setSubscriptionPlans] = useState<SubscriptionPlan[]>([]);
    const [configLoading, setConfigLoading] = useState(false);
    const [configEdits, setConfigEdits] = useState<Record<string, Partial<SubscriptionPlan>>>({});
    const [configSaving, setConfigSaving] = useState<string | null>(null);
    const [configMessage, setConfigMessage] = useState<{ planName: string; text: string; error: boolean } | null>(null);

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
        } else if (tab === 'config') {
            loadSubscriptionConfig();
        }
    }, [tab]);

    useEffect(() => {
        if (tab === 'stats') {
            loadStats(chartOffset);
        }
    }, [chartOffset]);

    useEffect(() => {
        if (tab === 'stats') {
            loadPortfolioStats(portfolioChartOffset);
        }
    }, [portfolioChartOffset]);

    const loadStats = (offset: number) => {
        setLoadingStats(true);
        Promise.all([
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/users?daysOffset=${offset}`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/portfolios?daysOffset=${portfolioChartOffset}`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/subscriptions`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/admin/stats/subscriptions/daily?daysOffset=${offset}`, { headers: authHeader() }),
        ]).then(([uRes, pRes, sRes, sdRes]) => {
            setUserStats(uRes.data);
            setPortfolioStats(pRes.data);
            setSubscriptionStats(sRes.data);
            setSubscriptionDailyStats(sdRes.data);
        }).catch(() => setError('Failed to load statistics.'))
          .finally(() => setLoadingStats(false));
    };

    const loadPortfolioStats = (offset: number) => {
        axios.get(`${API_GATEWAY}/api/v1/admin/stats/portfolios?daysOffset=${offset}`, { headers: authHeader() })
            .then(res => setPortfolioStats(res.data))
            .catch(() => {});
    };

    const loadSubscriptionConfig = () => {
        setConfigLoading(true);
        axios.get(`${API_GATEWAY}/api/v1/admin/subscription-config`, { headers: authHeader() })
            .then(res => {
                setSubscriptionPlans(res.data);
                const edits: Record<string, Partial<SubscriptionPlan>> = {};
                res.data.forEach((p: SubscriptionPlan) => { edits[p.planName] = { ...p }; });
                setConfigEdits(edits);
            })
            .catch(() => setError('Failed to load subscription config.'))
            .finally(() => setConfigLoading(false));
    };

    const saveConfig = (planName: string) => {
        const edit = configEdits[planName];
        if (!edit) return;
        setConfigSaving(planName);
        setConfigMessage(null);
        axios.put(`${API_GATEWAY}/api/v1/admin/subscription-config/${planName}`, {
            maxPortfolios: Number(edit.maxPortfolios),
            maxOptimizationsPerMonth: Number(edit.maxOptimizationsPerMonth),
            monthlyPrice: Number(edit.monthlyPrice),
            annualPrice: Number(edit.annualPrice),
        }, { headers: authHeader() })
            .then(res => {
                setSubscriptionPlans(prev => prev.map(p => p.planName === planName ? res.data : p));
                setConfigMessage({ planName, text: 'Saved', error: false });
                setTimeout(() => setConfigMessage(null), 3000);
            })
            .catch(() => setConfigMessage({ planName, text: 'Save failed', error: true }))
            .finally(() => setConfigSaving(null));
    };

    const updateConfigEdit = (planName: string, field: keyof SubscriptionPlan, value: string) => {
        setConfigEdits(prev => ({
            ...prev,
            [planName]: { ...prev[planName], [field]: field.startsWith('max') || field.endsWith('Price') ? value : value },
        }));
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

    const openIpModal = (user: AdminUser) => {
        setIpModal({ userId: user.id, username: user.username });
        setIpLoading(true);
        setIpAddresses([]);
        axios.get(`${API_GATEWAY}/api/v1/admin/users/${user.id}/ip-addresses`, { headers: authHeader() })
            .then(res => setIpAddresses(res.data))
            .finally(() => setIpLoading(false));
    };

    const openLinkedAccountsModal = (user: AdminUser) => {
        setLinkedModal({ userId: user.id, username: user.username });
        setLinkedLoading(true);
        setLinkedAccounts([]);
        axios.get(`${API_GATEWAY}/api/v1/admin/users/${user.id}/linked-accounts`, { headers: authHeader() })
            .then(res => setLinkedAccounts(res.data))
            .finally(() => setLinkedLoading(false));
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
                    <button style={tabStyle('config')} onClick={() => setTab('config')}>Config</button>
                </div>

                <div style={{ background: 'var(--bg-card)', borderRadius: '0 8px 8px 8px', border: '1px solid var(--border)', borderTop: 'none', padding: '1.5rem' }}>
                    {tab === 'users' && (
                        <>
                            {loadingUsers ? (
                                <div className="portfolio-loading">Loading users…</div>
                            ) : (
                                <>
                                <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap', marginBottom: '1rem', alignItems: 'center' }}>
                                    {([
                                        { field: 'username' as const, placeholder: 'Username' },
                                        { field: 'name' as const, placeholder: 'Name' },
                                        { field: 'email' as const, placeholder: 'Email' },
                                    ]).map(({ field, placeholder }) => (
                                        <input
                                            key={field}
                                            value={userFilters[field]}
                                            onChange={e => setFilter(field, e.target.value)}
                                            placeholder={placeholder}
                                            style={{
                                                background: 'var(--bg-dark)',
                                                color: 'var(--text-primary)',
                                                border: '1px solid var(--border)',
                                                borderRadius: 6,
                                                padding: '0.35rem 0.65rem',
                                                fontSize: '0.85rem',
                                                minWidth: 130,
                                            }}
                                        />
                                    ))}
                                    <select
                                        value={userFilters.plan}
                                        onChange={e => setFilter('plan', e.target.value)}
                                        style={{ background: 'var(--bg-dark)', color: 'var(--text-primary)', border: '1px solid var(--border)', borderRadius: 6, padding: '0.35rem 0.65rem', fontSize: '0.85rem' }}
                                    >
                                        <option value="">All Plans</option>
                                        <option value="FREE">Free</option>
                                        <option value="BASIC">Basic</option>
                                        <option value="PREMIUM">Premium</option>
                                    </select>
                                    <select
                                        value={userFilters.type}
                                        onChange={e => setFilter('type', e.target.value)}
                                        style={{ background: 'var(--bg-dark)', color: 'var(--text-primary)', border: '1px solid var(--border)', borderRadius: 6, padding: '0.35rem 0.65rem', fontSize: '0.85rem' }}
                                    >
                                        <option value="">All Types</option>
                                        <option value="10">Admin</option>
                                        <option value="8">User</option>
                                        <option value="6">Closed</option>
                                        <option value="4">Suspended</option>
                                        <option value="chargeback">Suspended – Chargebacks</option>
                                    </select>
                                    {(userFilters.username || userFilters.name || userFilters.email || userFilters.plan || userFilters.type) && (
                                        <button
                                            onClick={() => setUserFilters({ username: '', name: '', email: '', plan: '', type: '' })}
                                            style={{ background: 'transparent', color: 'var(--text-gray)', border: '1px solid var(--border)', borderRadius: 6, padding: '0.35rem 0.65rem', fontSize: '0.85rem', cursor: 'pointer' }}
                                        >
                                            Clear
                                        </button>
                                    )}
                                    <span style={{ fontSize: '0.82rem', color: 'var(--text-gray)', marginLeft: '0.25rem' }}>
                                        {sortedUsers.length} of {users.length}
                                    </span>
                                </div>
                                <div style={{ overflowX: 'auto' }}>
                                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                                        <thead>
                                            <tr style={{ borderBottom: '1px solid var(--border)' }}>
                                                {(
                                                    [
                                                        { label: 'Username', key: 'username' },
                                                        { label: 'Email', key: 'email' },
                                                        { label: 'Name', key: 'name' },
                                                        { label: 'Phone', key: 'phone' },
                                                        { label: 'Member Since', key: 'createdAt' },
                                                        { label: 'Last Active', key: 'lastActiveAt' },
                                                        { label: 'Plan', key: 'planName' },
                                                        { label: 'Type', key: 'userTypeName' },
                                                    ] as { label: string; key: UserSortKey }[]
                                                ).map(col => (
                                                    <th key={col.key}
                                                        style={{ ...thStyle, cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
                                                        onClick={() => handleUserSort(col.key)}
                                                    >
                                                        {col.label}
                                                        {userSort.key === col.key
                                                            ? (userSort.dir === 'asc' ? ' ▲' : ' ▼')
                                                            : ' ⇅'}
                                                    </th>
                                                ))}
                                                <th style={thStyle}>Change Type</th>
                                                <th style={thStyle}>Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {sortedUsers.map(u => (
                                                <tr key={u.id} style={{ borderBottom: '1px solid var(--border)' }}>
                                                    <td style={{ ...tdStyle, fontWeight: 600 }}>{u.username}</td>
                                                    <td style={tdStyle}>{u.email}</td>
                                                    <td style={tdStyle}>{[u.firstName, u.lastName].filter(Boolean).join(' ') || '—'}</td>
                                                    <td style={tdStyle}>{u.phone || '—'}</td>
                                                    <td style={tdStyle}>{u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}</td>
                                                    <td style={tdStyle}>{u.lastActiveAt ? new Date(u.lastActiveAt).toLocaleDateString() : '—'}</td>
                                                    <td style={tdStyle}>
                                                        <span style={{
                                                            background: u.planName === 'PREMIUM' ? '#6c47ff' : u.planName === 'BASIC' ? '#f59e0b' : '#6b7280',
                                                            color: '#fff',
                                                            borderRadius: 6,
                                                            padding: '0.2rem 0.6rem',
                                                            fontSize: '0.78rem',
                                                            fontWeight: 600,
                                                        }}>
                                                            {u.planName ?? 'FREE'}
                                                        </span>
                                                    </td>
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
                                                        <button
                                                            onClick={() => openIpModal(u)}
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
                                                            title="View IP addresses"
                                                        >
                                                            IP Addresses
                                                        </button>
                                                        <button
                                                            onClick={() => openLinkedAccountsModal(u)}
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
                                                            title="Show linked accounts sharing email, phone, or IP"
                                                        >
                                                            Linked Accounts
                                                        </button>
                                                    </td>
                                                </tr>
                                            ))}
                                            {sortedUsers.length === 0 && (
                                                <tr>
                                                    <td colSpan={10} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-gray)' }}>{users.length === 0 ? 'No users found.' : 'No users match the current filters.'}</td>
                                                </tr>
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                                </>
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
                                <StatsPanel userStats={userStats} portfolioStats={portfolioStats} subscriptionStats={subscriptionStats} subscriptionDailyStats={subscriptionDailyStats} chartOffset={chartOffset} setChartOffset={setChartOffset} portfolioChartOffset={portfolioChartOffset} setPortfolioChartOffset={setPortfolioChartOffset} />
                            )}
                        </>
                    )}

                    {tab === 'config' && (
                        <>
                            <h2 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '1.25rem', color: 'var(--text-primary)' }}>Subscription Plan Configuration</h2>
                            <p style={{ color: 'var(--text-gray)', fontSize: '0.9rem', marginBottom: '1.5rem' }}>
                                Adjust plan limits and pricing. Changes take effect immediately for new limit checks.
                            </p>
                            {configLoading ? (
                                <div className="portfolio-loading">Loading…</div>
                            ) : (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                                    {subscriptionPlans.map(plan => {
                                        const edit = configEdits[plan.planName] ?? plan;
                                        const msg = configMessage?.planName === plan.planName ? configMessage : null;
                                        return (
                                            <div key={plan.planName} style={{ background: 'var(--bg-input, #1e2035)', borderRadius: 10, border: '1px solid var(--border)', padding: '1.25rem' }}>
                                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
                                                    <h3 style={{ fontWeight: 700, fontSize: '1rem', color: 'var(--text-primary)', margin: 0 }}>{plan.displayName}</h3>
                                                    <span style={{ fontSize: '0.8rem', color: 'var(--text-gray)', background: 'var(--bg-card)', borderRadius: 4, padding: '0.15rem 0.5rem', border: '1px solid var(--border)' }}>{plan.planName}</span>
                                                </div>
                                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '1rem' }}>
                                                    {[
                                                        { label: 'Max Portfolios', field: 'maxPortfolios' as keyof SubscriptionPlan },
                                                        { label: 'Max Optimizations/Month', field: 'maxOptimizationsPerMonth' as keyof SubscriptionPlan },
                                                        { label: 'Monthly Price ($)', field: 'monthlyPrice' as keyof SubscriptionPlan },
                                                        { label: 'Annual Price ($)', field: 'annualPrice' as keyof SubscriptionPlan },
                                                    ].map(({ label, field }) => (
                                                        <div key={field}>
                                                            <label style={{ display: 'block', fontSize: '0.82rem', color: 'var(--text-gray)', marginBottom: 4 }}>{label}</label>
                                                            <input
                                                                type="number"
                                                                min={0}
                                                                value={String(edit[field] ?? '')}
                                                                onChange={e => updateConfigEdit(plan.planName, field, e.target.value)}
                                                                style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 6, padding: '0.35rem 0.6rem', color: 'var(--text-primary)', fontSize: '0.9rem', width: '100%' }}
                                                            />
                                                        </div>
                                                    ))}
                                                </div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginTop: '1rem' }}>
                                                    <button
                                                        onClick={() => saveConfig(plan.planName)}
                                                        disabled={configSaving === plan.planName}
                                                        style={{ background: '#6c47ff', color: '#fff', border: 'none', borderRadius: 6, padding: '0.4rem 1rem', fontWeight: 600, fontSize: '0.88rem', cursor: 'pointer' }}
                                                    >
                                                        {configSaving === plan.planName ? 'Saving…' : 'Save'}
                                                    </button>
                                                    {msg && (
                                                        <span style={{ fontSize: '0.85rem', color: msg.error ? '#ef4444' : '#22c55e', fontWeight: 600 }}>{msg.text}</span>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
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

            {linkedModal && (
                <div className="modal-overlay" onClick={() => setLinkedModal(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 680, width: '95%' }}>
                        <div className="modal-header">
                            <h2 className="modal-title">Linked Accounts — {linkedModal.username}</h2>
                            <button className="modal-close" onClick={() => setLinkedModal(null)}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem' }}>
                            {linkedLoading ? (
                                <p style={{ color: 'var(--text-gray)' }}>Loading…</p>
                            ) : linkedAccounts.length === 0 ? (
                                <p style={{ color: 'var(--text-gray)' }}>No linked accounts found.</p>
                            ) : (
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
                                    <thead>
                                        <tr style={{ borderBottom: '1px solid var(--border)' }}>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>User</th>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>Email</th>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>Shared Values</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {linkedAccounts.map((acct, i) => (
                                            <tr key={i} style={{ borderBottom: '1px solid var(--border)' }}>
                                                <td style={{ padding: '0.5rem 0.75rem', fontWeight: 600, color: 'var(--text-primary)' }}>{acct.username}</td>
                                                <td style={{ padding: '0.5rem 0.75rem', color: 'var(--text-gray)' }}>{acct.email ?? '—'}</td>
                                                <td style={{ padding: '0.5rem 0.75rem', color: 'var(--text-primary)' }}>
                                                    {acct.sharedValues.map((v, j) => (
                                                        <div key={j} style={{ background: 'rgba(99,102,241,0.12)', borderRadius: 4, padding: '0.15rem 0.5rem', marginBottom: 3, fontSize: '0.82rem', display: 'inline-block', marginRight: 4 }}>{v}</div>
                                                    ))}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {ipModal && (
                <div className="modal-overlay" onClick={() => setIpModal(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 620, width: '95%' }}>
                        <div className="modal-header">
                            <h2 className="modal-title">IP Addresses — {ipModal.username}</h2>
                            <button className="modal-close" onClick={() => setIpModal(null)}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem' }}>
                            {ipLoading ? (
                                <p style={{ color: 'var(--text-gray)' }}>Loading…</p>
                            ) : ipAddresses.length === 0 ? (
                                <p style={{ color: 'var(--text-gray)' }}>No IP addresses recorded yet.</p>
                            ) : (
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
                                    <thead>
                                        <tr style={{ borderBottom: '1px solid var(--border)' }}>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>IP Address</th>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>First Seen</th>
                                            <th style={{ textAlign: 'left', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>Last Seen</th>
                                            <th style={{ textAlign: 'right', padding: '0.4rem 0.75rem', color: 'var(--text-gray)', fontWeight: 600 }}>Requests</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {ipAddresses.map((ip, i) => (
                                            <tr key={i} style={{ borderBottom: '1px solid var(--border)' }}>
                                                <td style={{ padding: '0.5rem 0.75rem', fontFamily: 'monospace', color: 'var(--text-primary)' }}>{ip.ipAddress}</td>
                                                <td style={{ padding: '0.5rem 0.75rem', color: 'var(--text-gray)' }}>{new Date(ip.firstSeen).toLocaleString()}</td>
                                                <td style={{ padding: '0.5rem 0.75rem', color: 'var(--text-gray)' }}>{new Date(ip.lastSeen).toLocaleString()}</td>
                                                <td style={{ padding: '0.5rem 0.75rem', textAlign: 'right', color: 'var(--text-primary)' }}>{ip.requestCount.toLocaleString()}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Admin;
