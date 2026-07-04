import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getLoggedInUsername, isAdmin, isEmailVerified } from '../utils/auth';
import ImpersonationBanner from '../components/ImpersonationBanner';
import PortfolioDashboard from '../components/PortfolioDashboard';
import TransactionHistory from '../components/TransactionHistory';
import TradeForm from '../components/TradeForm';
import CashForm from '../components/CashForm';
import OptimizePanel from '../components/OptimizePanel';
import PortfolioProfileForm from '../components/PortfolioProfileForm';
import { listPortfolios, createPortfolio, updatePortfolio, deletePortfolio, savePortfolioProfile, getCashBalance, submitTransaction } from '../api/portfolioApi';
import type { Portfolio as PortfolioType } from '../api/portfolioApi';
import { getProfile, saveProfile, optimizePortfolio } from '../api/profileApi';
import { getAccountProfile, sendEmailVerification, verifyEmail, sendPhoneVerification, verifyPhone } from '../api/accountApi';
import { isPhoneVerified } from '../utils/auth';

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

    type WizardStep = 'ai-choice' | 'no-ai-name' | 'ai-name-profile' | 'ai-verify-email' | 'ai-verify-phone' | 'ai-model' | 'ai-cash' | 'ai-running';
    const [wizardStep, setWizardStep] = useState<WizardStep | null>(null);
    const [wizardName, setWizardName] = useState('');
    const [wizardDesc, setWizardDesc] = useState('');
    const [wizardProfile, setWizardProfile] = useState({
        riskLevel: null as string | null,
        goal: null as string | null,
        horizonYears: null as number | null,
        horizonStr: '',
        liquidityNeeds: null as string | null,
        sectorInput: '',
        additionalComments: '',
    });
    const [wizardError, setWizardError] = useState('');
    const [wizardSaving, setWizardSaving] = useState(false);
    const [wizardCreatedId, setWizardCreatedId] = useState<number | null>(null);
    const [wizardEmailSending, setWizardEmailSending] = useState(false);
    const [wizardEmail, setWizardEmail] = useState('');
    const [wizardEmailCode, setWizardEmailCode] = useState('');
    const [wizardEmailSent, setWizardEmailSent] = useState(false);
    const [wizardAiModel, setWizardAiModel] = useState<'chatgpt' | 'claude' | 'gemini'>('gemini');
    const [wizardCashBalance, setWizardCashBalance] = useState<number | null>(null);
    const [wizardCashAmount, setWizardCashAmount] = useState('');
    const [wizardCashSubmitting, setWizardCashSubmitting] = useState(false);
    const [wizardPhone, setWizardPhone] = useState('');
    const [wizardPhoneCode, setWizardPhoneCode] = useState('');
    const [wizardPhoneSent, setWizardPhoneSent] = useState(false);
    const [wizardPhoneSending, setWizardPhoneSending] = useState(false);

    const openWizard = async () => {
        setWizardStep('ai-choice');
        setWizardName('');
        setWizardDesc('');
        setWizardError('');
        setWizardCreatedId(null);
        setWizardEmailCode('');
        setWizardEmailSent(false);
        const investorProfile = await getProfile().catch(() => null);
        setWizardProfile({
            riskLevel: investorProfile?.riskLevel || null,
            goal: investorProfile?.goal || null,
            horizonYears: investorProfile?.horizonYears || null,
            horizonStr: investorProfile?.horizonYears ? String(investorProfile.horizonYears) : '',
            liquidityNeeds: investorProfile?.liquidityNeeds || null,
            sectorInput: (investorProfile?.sectorConstraints ?? []).join(', '),
            additionalComments: investorProfile?.additionalComments || '',
        });
    };
    const closeWizard = () => setWizardStep(null);

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

    const handleWizardSaveProfile = async () => {
        const name = wizardName.trim();
        if (!name) { setWizardError('Portfolio name is required.'); return; }
        const hasNotes = wizardProfile.additionalComments.trim().length > 0;
        if (!hasNotes) {
            if (!wizardProfile.riskLevel) { setWizardError('Risk Tolerance is required.'); return; }
            if (!wizardProfile.goal) { setWizardError('Primary Goal is required.'); return; }
            if (wizardProfile.horizonYears == null || wizardProfile.horizonYears < 1) { setWizardError('Time Horizon is required (1–50 years).'); return; }
            if (!wizardProfile.liquidityNeeds) { setWizardError('Liquidity Needs is required.'); return; }
        }
        setWizardError('');
        setWizardSaving(true);
        try {
            const created = await createPortfolio(name, wizardDesc.trim() || undefined);
            setWizardCreatedId(created.id);
            const sectors = wizardProfile.sectorInput.split(',').map(s => s.trim()).filter(Boolean);
            await savePortfolioProfile(created.id, {
                riskLevel: wizardProfile.riskLevel,
                goal: wizardProfile.goal,
                horizonYears: wizardProfile.horizonYears,
                liquidityNeeds: wizardProfile.liquidityNeeds,
                additionalComments: wizardProfile.additionalComments || null,
                sectorConstraints: sectors,
                currency: 'USD',
                portfolioId: created.id,
            });
            const existingInvestorProfile = await getProfile().catch(() => null);
            const isBlank = !existingInvestorProfile || (
                !existingInvestorProfile.riskLevel &&
                !existingInvestorProfile.goal &&
                !existingInvestorProfile.horizonYears &&
                !existingInvestorProfile.liquidityNeeds &&
                !existingInvestorProfile.additionalComments
            );
            if (isBlank) {
                await saveProfile({
                    riskLevel: wizardProfile.riskLevel ?? '',
                    goal: wizardProfile.goal ?? '',
                    horizonYears: wizardProfile.horizonYears ?? 0,
                    liquidityNeeds: wizardProfile.liquidityNeeds ?? '',
                    knowledgeLevel: '',
                    additionalComments: wizardProfile.additionalComments,
                    availableCash: existingInvestorProfile?.availableCash ?? 0,
                    currency: existingInvestorProfile?.currency ?? 'USD',
                    sectorConstraints: sectors,
                }).catch(() => {});
            }
            const updated = await listPortfolios();
            setPortfolios(updated);
            setActivePortfolioId(created.id);
            localStorage.setItem('activePortfolioId', String(created.id));
            markPortfolioReviewed(created.id);
            const acct = await getAccountProfile().catch(() => null);
            if (!isEmailVerified()) {
                setWizardEmailSent(false);
                setWizardEmailCode('');
                setWizardEmail(acct?.email ?? '');
                setWizardPhone(acct?.phone ?? '');
                setWizardStep('ai-verify-email');
            } else if (!isPhoneVerified() && acct?.phone) {
                setWizardPhone(acct.phone);
                setWizardPhoneSent(false);
                setWizardPhoneCode('');
                setWizardStep('ai-verify-phone');
            } else {
                setWizardStep('ai-model');
            }
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setWizardError(msg || 'Failed to save. Please try again.');
        } finally {
            setWizardSaving(false);
        }
    };

    const handleWizardSendEmailCode = async () => {
        setWizardEmailSending(true);
        setWizardError('');
        try {
            const resp = await sendEmailVerification(wizardEmail.trim() || undefined);
            if (resp.token) localStorage.setItem('token', resp.token);
            setWizardEmailSent(true);
        } catch {
            setWizardError('Failed to send verification code. Please try again.');
        } finally {
            setWizardEmailSending(false);
        }
    };

    const handleWizardVerifyEmail = async () => {
        if (!wizardEmailCode.trim()) { setWizardError('Please enter the verification code.'); return; }
        setWizardError('');
        setWizardSaving(true);
        try {
            const resp = await verifyEmail(wizardEmailCode.trim());
            if (resp.token) localStorage.setItem('token', resp.token);
            if (!isPhoneVerified() && wizardPhone) {
                setWizardPhoneSent(false);
                setWizardPhoneCode('');
                setWizardStep('ai-verify-phone');
            } else {
                setWizardStep('ai-model');
            }
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setWizardError(msg || 'Invalid or expired code. Please try again.');
        } finally {
            setWizardSaving(false);
        }
    };

    const handleWizardSendPhoneCode = async () => {
        setWizardPhoneSending(true);
        setWizardError('');
        try {
            const resp = await sendPhoneVerification();
            if (resp.token) localStorage.setItem('token', resp.token);
            setWizardPhoneSent(true);
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setWizardError(msg || 'Failed to send SMS code. Please try again.');
        } finally {
            setWizardPhoneSending(false);
        }
    };

    const handleWizardVerifyPhone = async () => {
        if (!wizardPhoneCode.trim()) { setWizardError('Please enter the verification code.'); return; }
        setWizardError('');
        setWizardSaving(true);
        try {
            const resp = await verifyPhone(wizardPhoneCode.trim());
            if (resp.token) localStorage.setItem('token', resp.token);
            setWizardStep('ai-model');
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setWizardError(msg || 'Invalid or expired code. Please try again.');
        } finally {
            setWizardSaving(false);
        }
    };

    const handleWizardGoToCash = async () => {
        if (!wizardCreatedId) return;
        setWizardError('');
        const bal = await getCashBalance(wizardCreatedId).catch(() => null);
        setWizardCashBalance(bal);
        setWizardCashAmount('');
        setWizardStep('ai-cash');
    };

    const handleWizardDeposit = async () => {
        if (!wizardCreatedId) return;
        const amt = parseFloat(wizardCashAmount);
        if (isNaN(amt) || amt <= 0) { setWizardError('Amount must be a positive number.'); return; }
        setWizardError('');
        setWizardCashSubmitting(true);
        try {
            await submitTransaction({ symbol: 'CASH', type: 'DEPOSIT', quantity: amt, price: 1 }, wizardCreatedId);
            await handleWizardRunAI();
        } catch {
            setWizardError('Deposit failed. Please try again.');
        } finally {
            setWizardCashSubmitting(false);
        }
    };

    const handleWizardRunAI = async () => {
        if (!wizardCreatedId) return;
        setWizardStep('ai-running');
        setWizardError('');
        try {
            await optimizePortfolio(wizardCreatedId, wizardAiModel);
            closeWizard();
            setActiveTab('optimize');
            setRefreshKey(k => k + 1);
        } catch {
            setWizardError('AI optimization failed. You can try again from the AI Optimize tab.');
            closeWizard();
            setActiveTab('optimize');
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
                            onClick={openWizard}
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
                            onClick={openWizard}>
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
                                onSaveSuccess={wizardStep === null ? () => setActiveTab('holdings') : undefined}
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

            {wizardStep === 'ai-choice' && (
                <div className="modal-overlay" onClick={closeWizard}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>New Portfolio</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <p style={{ color: 'var(--text-gray)', marginBottom: '1.5rem' }}>
                            Would you like AI to build an initial portfolio for you based on your investment profile?
                        </p>
                        <div style={{ display: 'flex', gap: '1rem' }}>
                            <button className="btn-primary-full" onClick={() => setWizardStep('ai-name-profile')}>
                                Yes, Use AI
                            </button>
                            <button className="btn-primary-full" style={{ background: 'var(--card-bg)', border: '1px solid var(--border)' }}
                                onClick={() => setWizardStep('no-ai-name')}>
                                No, Just Create
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {wizardStep === 'no-ai-name' && (
                <div className="modal-overlay" onClick={closeWizard}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>New Portfolio</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
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
                            rows={3}
                            style={{ resize: 'vertical', width: '100%', boxSizing: 'border-box' }}
                        />
                        {portfolioError && <div className="error-msg">{portfolioError}</div>}
                        <button className="btn-primary-full" onClick={async () => {
                            await handleCreatePortfolio();
                            closeWizard();
                        }}>
                            Create Portfolio
                        </button>
                    </div>
                </div>
            )}

            {wizardStep === 'ai-name-profile' && (
                <div className="modal-overlay">
                    <div className="modal-card" style={{ maxWidth: 560, maxHeight: '90vh', overflowY: 'auto' }} onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Portfolio Investment Profile</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <p style={{ color: 'var(--text-gray)', fontSize: '0.88rem', marginBottom: '1.25rem' }}>
                            Tell us about this portfolio so the AI can build your initial holdings.
                            Fields marked with <span style={{ color: '#f87171' }}>*</span> are required unless you fill in Additional Notes instead.
                        </p>

                        <label className="form-label">Portfolio Name <span style={{ color: '#f87171' }}>*</span></label>
                        <input
                            type="text"
                            placeholder="e.g. Retirement, Growth Fund"
                            value={wizardName}
                            onChange={e => setWizardName(e.target.value)}
                            style={{ marginBottom: '1rem' }}
                            autoFocus
                        />

                        <label className="form-label">Description (optional)</label>
                        <textarea
                            placeholder="Brief description"
                            value={wizardDesc}
                            onChange={e => setWizardDesc(e.target.value)}
                            rows={2}
                            style={{ resize: 'vertical', width: '100%', boxSizing: 'border-box', marginBottom: '1rem' }}
                        />

                        <label className="form-label">Risk Tolerance <span style={{ color: '#f87171' }}>*</span></label>
                        <select
                            value={wizardProfile.riskLevel ?? ''}
                            onChange={e => setWizardProfile(p => ({ ...p, riskLevel: e.target.value || null }))}
                            style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
                        >
                            <option value="">Select…</option>
                            <option value="conservative">Conservative</option>
                            <option value="moderate">Moderate</option>
                            <option value="moderate_aggressive">Moderate Aggressive</option>
                            <option value="aggressive">Aggressive</option>
                        </select>

                        <label className="form-label">Primary Goal <span style={{ color: '#f87171' }}>*</span></label>
                        <select
                            value={wizardProfile.goal ?? ''}
                            onChange={e => setWizardProfile(p => ({ ...p, goal: e.target.value || null }))}
                            style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
                        >
                            <option value="">Select…</option>
                            <option value="income">Income</option>
                            <option value="balanced">Balanced</option>
                            <option value="growth">Growth</option>
                            <option value="speculation">Speculation</option>
                        </select>

                        <label className="form-label">Time Horizon (years) <span style={{ color: '#f87171' }}>*</span></label>
                        <input
                            type="number"
                            min={1}
                            max={50}
                            placeholder="1–50"
                            value={wizardProfile.horizonStr}
                            onChange={e => {
                                const n = parseInt(e.target.value);
                                setWizardProfile(p => ({ ...p, horizonStr: e.target.value, horizonYears: isNaN(n) ? null : n }));
                            }}
                            style={{ marginBottom: '1rem' }}
                        />

                        <label className="form-label">Liquidity Needs <span style={{ color: '#f87171' }}>*</span></label>
                        <select
                            value={wizardProfile.liquidityNeeds ?? ''}
                            onChange={e => setWizardProfile(p => ({ ...p, liquidityNeeds: e.target.value || null }))}
                            style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
                        >
                            <option value="">Select…</option>
                            <option value="low">Low</option>
                            <option value="medium">Medium</option>
                            <option value="high">High</option>
                        </select>

                        <label className="form-label">Preferred Sectors (comma-separated, optional)</label>
                        <input
                            type="text"
                            placeholder="e.g. Technology, Healthcare"
                            value={wizardProfile.sectorInput}
                            onChange={e => setWizardProfile(p => ({ ...p, sectorInput: e.target.value }))}
                            style={{ marginBottom: '1rem' }}
                        />

                        <label className="form-label">Additional Notes (optional — replaces required fields above)</label>
                        <textarea
                            placeholder="Describe any specific investment strategy, constraints, or goals…"
                            value={wizardProfile.additionalComments}
                            onChange={e => setWizardProfile(p => ({ ...p, additionalComments: e.target.value }))}
                            rows={4}
                            style={{ resize: 'vertical', width: '100%', boxSizing: 'border-box', marginBottom: '1.25rem' }}
                        />

                        {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}

                        <button className="btn-primary-full" onClick={handleWizardSaveProfile} disabled={wizardSaving}>
                            {wizardSaving ? 'Saving…' : 'Save & Continue'}
                        </button>
                    </div>
                </div>
            )}

            {wizardStep === 'ai-verify-email' && (
                <div className="modal-overlay">
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Verify Your Email</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <p style={{ color: 'var(--text-gray)', marginBottom: '1.25rem' }}>
                            Before using AI optimization, please verify your email address.
                        </p>
                        {!wizardEmailSent ? (
                            <>
                                <label className="form-label">Email Address</label>
                                <input
                                    type="email"
                                    value={wizardEmail}
                                    onChange={e => setWizardEmail(e.target.value)}
                                    placeholder="your@email.com"
                                    style={{ marginBottom: '1rem' }}
                                />
                                <p style={{ color: 'var(--text-gray)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                                    A verification code will be sent to this address. Update it above if needed.
                                </p>
                                {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}
                                <button className="btn-primary-full" onClick={handleWizardSendEmailCode} disabled={wizardEmailSending || !wizardEmail.trim()}>
                                    {wizardEmailSending ? 'Sending…' : 'Send Verification Code'}
                                </button>
                            </>
                        ) : (
                            <>
                                <p style={{ color: '#22c55e', fontSize: '0.88rem', marginBottom: '1rem' }}>
                                    A 6-digit code has been sent to your email address.
                                </p>
                                <label className="form-label">Enter Verification Code</label>
                                <input
                                    type="text"
                                    placeholder="123456"
                                    value={wizardEmailCode}
                                    onChange={e => setWizardEmailCode(e.target.value)}
                                    style={{ marginBottom: '1rem' }}
                                    autoFocus
                                />
                                {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}
                                <div style={{ display: 'flex', gap: '0.75rem' }}>
                                    <button className="btn-primary-full" onClick={handleWizardVerifyEmail} disabled={wizardSaving}>
                                        {wizardSaving ? 'Verifying…' : 'Verify & Continue'}
                                    </button>
                                    <button className="btn-primary-full" style={{ background: 'transparent', border: '1px solid var(--border)', fontSize: '0.85rem' }}
                                        onClick={() => { setWizardEmailSent(false); setWizardError(''); }}>
                                        Resend
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}

            {wizardStep === 'ai-verify-phone' && (
                <div className="modal-overlay">
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Verify Cell Phone</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <p style={{ color: 'var(--text-gray)', marginBottom: '1.25rem' }}>
                            Before using AI optimization, please verify your cell phone number.
                        </p>
                        {!wizardPhoneSent ? (
                            <>
                                <label className="form-label">Cell Phone Number</label>
                                <input
                                    type="text"
                                    value={wizardPhone}
                                    readOnly
                                    style={{ marginBottom: '1rem', opacity: 0.7 }}
                                />
                                <p style={{ color: 'var(--text-gray)', fontSize: '0.85rem', marginBottom: '1rem' }}>
                                    A verification code will be sent to this number via SMS.
                                </p>
                                {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}
                                <button className="btn-primary-full" onClick={handleWizardSendPhoneCode} disabled={wizardPhoneSending}>
                                    {wizardPhoneSending ? 'Sending…' : 'Send SMS Code'}
                                </button>
                            </>
                        ) : (
                            <>
                                <p style={{ color: '#22c55e', fontSize: '0.88rem', marginBottom: '1rem' }}>
                                    A 6-digit code has been sent to your cell phone.
                                </p>
                                <label className="form-label">Enter Verification Code</label>
                                <input
                                    type="text"
                                    placeholder="123456"
                                    value={wizardPhoneCode}
                                    onChange={e => setWizardPhoneCode(e.target.value)}
                                    style={{ marginBottom: '1rem' }}
                                    autoFocus
                                />
                                {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}
                                <div style={{ display: 'flex', gap: '0.75rem' }}>
                                    <button className="btn-primary-full" onClick={handleWizardVerifyPhone} disabled={wizardSaving}>
                                        {wizardSaving ? 'Verifying…' : 'Verify & Continue'}
                                    </button>
                                    <button className="btn-primary-full" style={{ background: 'transparent', border: '1px solid var(--border)', fontSize: '0.85rem' }}
                                        onClick={() => { setWizardPhoneSent(false); setWizardError(''); }}>
                                        Resend
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}

            {wizardStep === 'ai-model' && (
                <div className="modal-overlay">
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Choose AI Model</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <p style={{ color: 'var(--text-gray)', marginBottom: '1.5rem' }}>
                            Select which AI model you'd like to use to generate your initial portfolio recommendations.
                        </p>
                        {(['gemini', 'chatgpt', 'claude'] as const).map(model => (
                            <label key={model} style={{
                                display: 'flex', alignItems: 'center', gap: '0.75rem',
                                padding: '0.75rem 1rem', borderRadius: 8, cursor: 'pointer',
                                marginBottom: '0.5rem',
                                background: wizardAiModel === model ? '#1e2a4a' : 'transparent',
                                border: `1px solid ${wizardAiModel === model ? '#818cf8' : 'var(--border)'}`,
                            }}>
                                <input type="radio" name="aiModel" value={model} checked={wizardAiModel === model}
                                    onChange={() => setWizardAiModel(model)} />
                                <span style={{ fontWeight: 600, color: 'var(--text-light)', textTransform: 'capitalize' }}>
                                    {model === 'chatgpt' ? 'ChatGPT' : model === 'claude' ? 'Claude' : 'Gemini'}
                                </span>
                            </label>
                        ))}
                        <button className="btn-primary-full" style={{ marginTop: '1rem' }} onClick={handleWizardGoToCash}>
                            Next
                        </button>
                    </div>
                </div>
            )}

            {wizardStep === 'ai-cash' && (
                <div className="modal-overlay">
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>Deposit Cash</h2>
                            <button className="modal-close" onClick={closeWizard}>✕</button>
                        </div>
                        <div style={{ textAlign: 'center', marginBottom: '1.25rem', fontSize: '1.05rem', color: 'var(--text-light)' }}>
                            Available Balance:&nbsp;
                            <strong>
                                {wizardCashBalance !== null
                                    ? `$${wizardCashBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                                    : '$0.00'}
                            </strong>
                        </div>
                        <p style={{ color: 'var(--text-gray)', fontSize: '0.88rem', marginBottom: '1.25rem' }}>
                            Deposit funds now so the AI can size your recommendations. You can always deposit more later.
                        </p>
                        <label className="form-label">Deposit Amount ($)</label>
                        <input
                            type="number"
                            placeholder="0.00"
                            min="0.01"
                            step="any"
                            value={wizardCashAmount}
                            onChange={e => { setWizardCashAmount(e.target.value); setWizardError(''); }}
                            style={{ marginBottom: '1rem' }}
                            autoFocus
                        />
                        {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}
                        <div style={{ display: 'flex', gap: '0.75rem' }}>
                            <button className="btn-primary-full" onClick={handleWizardDeposit} disabled={wizardCashSubmitting || !wizardCashAmount.trim()}>
                                {wizardCashSubmitting ? 'Depositing…' : 'Deposit & Build Portfolio'}
                            </button>
                            <button
                                className="btn-primary-full"
                                style={{ background: 'transparent', border: '1px solid var(--border)', whiteSpace: 'nowrap' }}
                                onClick={handleWizardRunAI}
                                disabled={wizardCashSubmitting}
                            >
                                Skip
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {wizardStep === 'ai-running' && (
                <div className="modal-overlay">
                    <div className="modal-card" style={{ textAlign: 'center' }} onClick={e => e.stopPropagation()}>
                        <h2 style={{ marginBottom: '1rem' }}>Building Your Portfolio…</h2>
                        <p style={{ color: 'var(--text-gray)', marginBottom: '1.5rem' }}>
                            The AI is analyzing your investment profile and generating recommendations. This may take a moment.
                        </p>
                        <div style={{ fontSize: '2rem', animation: 'spin 1s linear infinite' }}>⏳</div>
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
