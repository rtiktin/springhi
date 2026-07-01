import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getLoggedInUsername, isAdmin } from '../utils/auth';
import ImpersonationBanner from '../components/ImpersonationBanner';
import PortfolioDashboard from '../components/PortfolioDashboard';
import TransactionHistory from '../components/TransactionHistory';
import TradeForm from '../components/TradeForm';
import CashForm from '../components/CashForm';
import OptimizePanel from '../components/OptimizePanel';
import PortfolioProfileForm from '../components/PortfolioProfileForm';
import { listPortfolios, createPortfolio, updatePortfolio, deletePortfolio, getOrCreateDefaultPortfolio } from '../api/portfolioApi';
import type { Portfolio as PortfolioType } from '../api/portfolioApi';

type Tab = 'holdings' | 'transactions' | 'optimize' | 'profile';

const Portfolio: React.FC = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const username = getLoggedInUsername();
    const [activeTab, setActiveTab] = useState<Tab>('holdings');
    const [showTradeForm, setShowTradeForm] = useState(false);
    const [showCashForm, setShowCashForm] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);

    const [portfolios, setPortfolios] = useState<PortfolioType[]>([]);
    const [activePortfolioId, setActivePortfolioId] = useState<number | null>(null);
    const [portfolioLoading, setPortfolioLoading] = useState(true);
    const [showNewPortfolioForm, setShowNewPortfolioForm] = useState(false);
    const [newPortfolioName, setNewPortfolioName] = useState('');
    const [newPortfolioDesc, setNewPortfolioDesc] = useState('');
    const [showRenameForm, setShowRenameForm] = useState(false);
    const [renameValue, setRenameValue] = useState('');
    const [portfolioError, setPortfolioError] = useState('');
    const [profileBannerMsg, setProfileBannerMsg] = useState('');
    const [reviewedPortfolioIds, setReviewedPortfolioIds] = useState<Set<number>>(() => {
        try {
            const stored = localStorage.getItem('reviewedPortfolioIds');
            return stored ? new Set<number>(JSON.parse(stored)) : new Set<number>();
        } catch {
            return new Set<number>();
        }
    });

    useEffect(() => {
        const tab = searchParams.get('tab') as Tab | null;
        if (tab) setActiveTab(tab);
    }, [searchParams]);

    useEffect(() => {
        const savedId = localStorage.getItem('activePortfolioId');
        listPortfolios()
            .then(ps => {
                setPortfolios(ps);
                if (ps.length === 0) return;
                const saved = savedId ? ps.find(p => p.id === Number(savedId)) : null;
                if (saved) {
                    setActivePortfolioId(saved.id);
                } else {
                    setActivePortfolioId(ps[0].id);
                    localStorage.setItem('activePortfolioId', String(ps[0].id));
                }
            })
            .catch(() => {})
            .finally(() => setPortfolioLoading(false));
    }, []);

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    const handleTradeSuccess = () => {
        setRefreshKey(k => k + 1);
    };

    const markPortfolioReviewed = (id: number) => {
        setReviewedPortfolioIds(prev => {
            const next = new Set(prev);
            next.add(id);
            localStorage.setItem('reviewedPortfolioIds', JSON.stringify([...next]));
            return next;
        });
        setProfileBannerMsg('');
    };

    const handleCreatePortfolio = async () => {
        const name = newPortfolioName.trim();
        if (!name) return;
        setPortfolioError('');
        try {
            const created = await createPortfolio(name, newPortfolioDesc.trim() || undefined);
            const updated = await listPortfolios();
            setPortfolios(updated);
            setActivePortfolioId(created.id);
            localStorage.setItem('activePortfolioId', String(created.id));
            setShowNewPortfolioForm(false);
            setNewPortfolioName('');
            setNewPortfolioDesc('');
            setActiveTab('profile');
            setProfileBannerMsg('Please review your Portfolio Profile settings before using the AI Optimize function.');
            markPortfolioReviewed(created.id);
        } catch {
            setPortfolioError('Failed to create portfolio.');
        }
    };

    const handleRenamePortfolio = async () => {
        if (!activePortfolioId || !renameValue.trim()) return;
        setPortfolioError('');
        try {
            const current = portfolios.find(p => p.id === activePortfolioId);
            await updatePortfolio(activePortfolioId, renameValue.trim(), current?.description);
            const updated = await listPortfolios();
            setPortfolios(updated);
            setShowRenameForm(false);
        } catch {
            setPortfolioError('Failed to rename portfolio.');
        }
    };

    const handleDeletePortfolio = async () => {
        if (!activePortfolioId) return;
        if (!window.confirm('Delete this portfolio? This cannot be undone.')) return;
        setPortfolioError('');
        try {
            await deletePortfolio(activePortfolioId);
            const updated = await listPortfolios();
            setPortfolios(updated);
            setActivePortfolioId(updated.length > 0 ? updated[0].id : null);
        } catch {
            setPortfolioError('Failed to delete portfolio.');
        }
    };

    const activePortfolio = portfolios.find(p => p.id === activePortfolioId);

    if (portfolioLoading) {
        return (
            <div className="portfolio-page">
                <div className="portfolio-loading">Loading portfolios…</div>
            </div>
        );
    }

    return (
        <div className="portfolio-page">
            <ImpersonationBanner />
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/account" className="btn-logout">Account</Link>
                    <Link to="/profile" className="btn-logout">Investor Profile</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    {isAdmin() && <Link to="/admin" className="btn-logout">Admin</Link>}
                    <button className="btn-trade" onClick={() => setShowCashForm(true)}>
                        $ Cash
                    </button>
                    <button className="btn-trade" onClick={() => setShowTradeForm(true)}>
                        + Trade
                    </button>
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <div className="portfolio-header-row">
                    <div>
                        <h1 className="portfolio-heading">My Portfolio</h1>
                        <p className="portfolio-sub">Prices updated at 9am &amp; 3pm ET on market days.</p>
                    </div>
                </div>

                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                    marginBottom: '1rem',
                    flexWrap: 'wrap',
                }}>
                    {portfolios.length > 0 && (
                        <>
                            <label style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Portfolio:</label>
                            <select
                                value={activePortfolioId ?? ''}
                                onChange={e => {
                                    const id = Number(e.target.value);
                                    setActivePortfolioId(id);
                                    localStorage.setItem('activePortfolioId', String(id));
                                    setRefreshKey(k => k + 1);
                                }}
                                style={{
                                    padding: '0.4rem 0.75rem',
                                    borderRadius: '6px',
                                    border: '1px solid var(--border)',
                                    background: '#1e2030',
                                    color: '#e2e8f0',
                                    fontSize: '0.95rem',
                                }}
                            >
                                {portfolios.map(p => (
                                    <option key={p.id} value={p.id} style={{ background: '#1e2030', color: '#e2e8f0' }}>{p.name}</option>
                                ))}
                            </select>
                            <button
                                className="btn-trade"
                                style={{ fontSize: '0.85rem', padding: '0.35rem 0.7rem' }}
                                onClick={() => {
                                    setRenameValue(activePortfolio?.name ?? '');
                                    setShowRenameForm(true);
                                }}
                            >
                                Rename
                            </button>
                        </>
                    )}
                    {portfolios.length > 0 && (
                        <button
                            className="btn-logout"
                            style={{ fontSize: '0.85rem', padding: '0.35rem 0.7rem' }}
                            onClick={() => setShowNewPortfolioForm(true)}
                        >
                            + New Portfolio
                        </button>
                    )}
                    {portfolios.length > 1 && (
                        <button
                            className="btn-logout"
                            style={{ fontSize: '0.85rem', padding: '0.35rem 0.7rem', color: '#e55' }}
                            onClick={handleDeletePortfolio}
                        >
                            Delete
                        </button>
                    )}
                    {portfolioError && (
                        <span style={{ color: '#e55', fontSize: '0.85rem' }}>{portfolioError}</span>
                    )}
                </div>

                {!activePortfolioId ? (
                    <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-gray)' }}>
                        <p>No portfolio found. Create your first portfolio to get started.</p>
                        <button className="btn-primary-full" style={{ marginTop: '1rem', maxWidth: 240 }}
                            onClick={() => setShowNewPortfolioForm(true)}>
                            Create Portfolio
                        </button>
                    </div>
                ) : (
                    <>
                        <div className="tab-bar">
                            <button
                                className={`tab-btn ${activeTab === 'holdings' ? 'tab-active' : ''}`}
                                onClick={() => { setActiveTab('holdings'); setProfileBannerMsg(''); }}
                            >
                                Holdings
                            </button>
                            <button
                                className={`tab-btn ${activeTab === 'transactions' ? 'tab-active' : ''}`}
                                onClick={() => { setActiveTab('transactions'); setProfileBannerMsg(''); }}
                            >
                                Transactions
                            </button>
                            <button
                                className={`tab-btn ${activeTab === 'optimize' ? 'tab-active' : ''}`}
                                onClick={() => {
                                    if (activePortfolioId && !reviewedPortfolioIds.has(activePortfolioId)) {
                                        setActiveTab('profile');
                                        setProfileBannerMsg('Please review your Portfolio Profile settings before using the AI Optimize function.');
                                        markPortfolioReviewed(activePortfolioId);
                                    } else {
                                        setActiveTab('optimize');
                                    }
                                }}
                            >
                                AI Optimize
                            </button>
                            <button
                                className={`tab-btn ${activeTab === 'profile' ? 'tab-active' : ''}`}
                                onClick={() => setActiveTab('profile')}
                            >
                                Profile
                            </button>
                        </div>

                        {activeTab === 'holdings' && (
                            <PortfolioDashboard key={`holdings-${activePortfolioId}-${refreshKey}`} portfolioId={activePortfolioId} />
                        )}
                        {activeTab === 'transactions' && (
                            <TransactionHistory key={`tx-${activePortfolioId}-${refreshKey}`} portfolioId={activePortfolioId} />
                        )}
                        {activeTab === 'optimize' && (
                            <OptimizePanel key={`opt-${activePortfolioId}`} portfolioId={activePortfolioId} onTradeSuccess={handleTradeSuccess} onNavigateToProfile={() => setActiveTab('profile')} cashRefreshSignal={refreshKey} />
                        )}
                        {activeTab === 'profile' && (
                            <PortfolioProfileForm
                                key={`prof-${activePortfolioId}`}
                                portfolioId={activePortfolioId}
                                bannerMessage={profileBannerMsg}
                            />
                        )}
                    </>
                )}
            </main>

            {showTradeForm && activePortfolioId && (
                <TradeForm
                    portfolioId={activePortfolioId}
                    onClose={() => setShowTradeForm(false)}
                    onSuccess={handleTradeSuccess}
                />
            )}

            {showCashForm && activePortfolioId && (
                <CashForm
                    portfolioId={activePortfolioId}
                    onClose={() => setShowCashForm(false)}
                    onSuccess={handleTradeSuccess}
                />
            )}

            {showNewPortfolioForm && (
                <div className="modal-overlay" onClick={() => setShowNewPortfolioForm(false)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>New Portfolio</h2>
                            <button className="modal-close" onClick={() => setShowNewPortfolioForm(false)}>✕</button>
                        </div>
                        <label className="form-label">Name</label>
                        <input
                            type="text"
                            placeholder="e.g. Retirement, Growth Fund"
                            value={newPortfolioName}
                            onChange={e => setNewPortfolioName(e.target.value)}
                            autoFocus
                        />
                        <label className="form-label">Description (optional)</label>
                        <textarea
                            placeholder="Brief description"
                            value={newPortfolioDesc}
                            onChange={e => setNewPortfolioDesc(e.target.value)}
                            rows={4}
                            style={{ resize: 'vertical', width: '100%', boxSizing: 'border-box' }}
                        />
                        {portfolioError && <div className="error-msg">{portfolioError}</div>}
                        <button className="btn-primary-full" onClick={handleCreatePortfolio}>
                            Create Portfolio
                        </button>
                    </div>
                </div>
            )}

            {showRenameForm && (
                <div className="modal-overlay" onClick={() => setShowRenameForm(false)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Rename Portfolio</h2>
                            <button className="modal-close" onClick={() => setShowRenameForm(false)}>✕</button>
                        </div>
                        <label className="form-label">New Name</label>
                        <input
                            type="text"
                            value={renameValue}
                            onChange={e => setRenameValue(e.target.value)}
                            autoFocus
                        />
                        {portfolioError && <div className="error-msg">{portfolioError}</div>}
                        <button className="btn-primary-full" onClick={handleRenamePortfolio}>
                            Save
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Portfolio;
