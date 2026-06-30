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
    createdAt: string;
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
};

type AdminTab = 'users' | 'portfolios';

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

    useEffect(() => {
        if (!isAdmin()) {
            navigate('/portfolio');
        }
    }, [navigate]);

    useEffect(() => {
        if (tab === 'users') {
            loadUsers();
        } else {
            loadPortfolios();
        }
    }, [tab]);

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

    const handleTypeChange = (userId: number, newType: number) => {
        setChangingType(userId);
        axios.put(`${API_GATEWAY}/api/v1/admin/users/${userId}/type`, { userType: newType }, { headers: authHeader() })
            .then(res => {
                setUsers(prev => prev.map(u => u.id === userId ? { ...u, userType: res.data.userType, userTypeName: res.data.userTypeName } : u));
            })
            .catch(() => setError('Failed to update user type.'))
            .finally(() => setChangingType(null));
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
                                                    <td style={tdStyle}>
                                                        <span style={{
                                                            background: TYPE_BADGE_COLOR[u.userType] ?? '#6b7280',
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
                                                            value={u.userType}
                                                            disabled={changingType === u.id}
                                                            onChange={e => handleTypeChange(u.id, parseInt(e.target.value))}
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
                                                        </select>
                                                    </td>
                                                    <td style={{ ...tdStyle, display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
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
                                                    <td colSpan={8} style={{ textAlign: 'center', padding: '2rem', color: 'var(--text-gray)' }}>No users found.</td>
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
                </div>
            </main>

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
        </div>
    );
};

export default Admin;
