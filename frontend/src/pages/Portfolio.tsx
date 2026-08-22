import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getLoggedInUsername, isAdmin, isEmailVerified } from '../utils/auth';
import ImpersonationBanner from '../components/ImpersonationBanner';
import PortfolioDashboard from '../components/PortfolioDashboard';
import TransactionHistory from '../components/TransactionHistory';
import TradeForm from '../components/TradeForm';
import CashForm from '../components/CashForm';
import OptimizePanel from '../components/OptimizePanel';
import ScheduleManager from '../components/ScheduleManager';
import PortfolioProfileForm from '../components/PortfolioProfileForm';
import { listPortfolios, createPortfolio, updatePortfolio, deletePortfolio, savePortfolioProfile, getCashBalance, submitTransaction, getPortfoliosCreatedCount, getOptimizationQuota, getPortfolioQuota, getAiRunTimestamps, getAiRunDetails } from '../api/portfolioApi';
import type { Portfolio as PortfolioType, AiRunDetails } from '../api/portfolioApi';
import { getProfile, saveProfile, optimizePortfolio } from '../api/profileApi';
import { getAccountProfile, sendEmailVerification, verifyEmail, sendPhoneVerification, verifyPhone } from '../api/accountApi';
import { isPhoneVerified } from '../utils/auth';

type Tab = 'holdings' | 'transactions' | 'aiOptimizations' | 'optimize' | 'profile';



const Portfolio: React.FC = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const username = getLoggedInUsername();
    const [activeTab, setActiveTab] = useState<Tab>('holdings');
    const [showTradeForm, setShowTradeForm] = useState(false);
    const [showCashForm, setShowCashForm] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [optimizeKey, setOptimizeKey] = useState(0);

    const [aiRunTimestamps, setAiRunTimestamps] = useState<string[]>([]);
    const [aiRunsLoaded, setAiRunsLoaded] = useState(false);
    const [aiRunsLoading, setAiRunsLoading] = useState(false);
    const [aiRunsError, setAiRunsError] = useState('');
    const [aiRunsPage, setAiRunsPage] = useState(0);
    const AI_RUNS_PAGE_SIZE = 5;
    const [expandedRunDetails, setExpandedRunDetails] = useState<Record<string, AiRunDetails>>({});
    const [expandedRunLoading, setExpandedRunLoading] = useState<Record<string, boolean>>({});

    const [portfolios, setPortfolios] = useState<PortfolioType[]>([]);
    const [activePortfolioId, setActivePortfolioId] = useState<number | null>(null);
    const [portfolioLoading, setPortfolioLoading] = useState(true);
    const [portfolioQuota, setPortfolioQuota] = useState<{ used: number; max: number; planName: string } | null>(null);
    const [optimizationQuota, setOptimizationQuota] = useState<{ used: number; scheduled: number; max: number; isFree: boolean } | null>(null);
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
    const [wizardTotalCreated, setWizardTotalCreated] = useState(0);

    const openWizard = async () => {
        try {
            const quota = await getPortfolioQuota();
            if (quota.used >= quota.max) {
                setUpgradeModal({ message: `You have reached the maximum number of portfolios (${quota.max}) for your ${quota.planName} plan. Please upgrade to create more.` });
                return;
            }
        } catch (err) {
            console.error('Failed to fetch portfolio quota:', err);
        }
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
    const [upgradeModal, setUpgradeModal] = useState<{ message: string } | null>(null);
    const [wizardQuotaChecking, setWizardQuotaChecking] = useState(false);
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
        Promise.all([listPortfolios(), getPortfolioQuota(), getOptimizationQuota()])
            .then(([ps, quota, optQuota]) => {
                setPortfolios(ps);
                setPortfolioQuota(quota);
                setOptimizationQuota(optQuota);
                if (ps.length === 0) return;
                
                const firstEnabled = ps.find(p => p.enabled);
                const saved = savedId ? ps.find(p => p.id === Number(savedId)) : null;
                
                if (saved && saved.enabled) {
                    setActivePortfolioId(saved.id);
                } else if (firstEnabled) {
                    setActivePortfolioId(firstEnabled.id);
                    localStorage.setItem('activePortfolioId', String(firstEnabled.id));
                } else {
                    setActivePortfolioId(ps[0].id);
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
        } catch (err: unknown) {
            const data = (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data;
            if (data?.error === 'PORTFOLIO_LIMIT_REACHED') {
                setUpgradeModal({ message: data.message ?? 'Portfolio limit reached.' });
            } else {
                setPortfolioError('Failed to create portfolio.');
            }
        }
    };

    const handleChooseAI = async () => {
        setWizardQuotaChecking(true);
        try {
            const quota = await getOptimizationQuota();
            if (quota.used + quota.scheduled >= quota.max) {
                const limitLabel = quota.isFree ? 'ever' : 'for this month';
                const planLabel = quota.isFree ? 'FREE' : 'current';
                const scheduledNote = quota.scheduled > 0 ? ` (including ~${Math.round(quota.scheduled * 10) / 10} reserved by schedules)` : '';
                closeWizard();
                setUpgradeModal({ message: `You have used all ${quota.max} AI optimization(s) ${limitLabel} on your ${planLabel} plan${scheduledNote}. Please upgrade to run more.` });
                return;
            }
            setWizardStep('ai-name-profile');
        } catch {
            setWizardStep('ai-name-profile');
        } finally {
            setWizardQuotaChecking(false);
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
            const [acct, totalCreated] = await Promise.all([
                getAccountProfile().catch(() => null),
                getPortfoliosCreatedCount().catch(() => 0),
            ]);
            setWizardTotalCreated(totalCreated);
            const emailOk = acct?.emailVerified === true || isEmailVerified();
            const phoneOk = acct?.phoneVerified === true || isPhoneVerified();
            if (!emailOk) {
                setWizardEmailSent(false);
                setWizardEmailCode('');
                setWizardEmail(acct?.email ?? '');
                setWizardPhone(acct?.phone ?? '');
                setWizardStep('ai-verify-email');
            } else if (totalCreated >= 1 && !phoneOk) {
                setWizardPhone(acct?.phone ?? '');
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
            if (wizardTotalCreated >= 1 && !isPhoneVerified()) {
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
            const resp = await sendPhoneVerification(wizardPhone.trim() || undefined);
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
        setWizardError('');
        setWizardSaving(true);
        try {
            const created = await createPortfolio(wizardName.trim(), wizardDesc.trim() || undefined);
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
            const [updated, quota, optQuota] = await Promise.all([listPortfolios(), getPortfolioQuota(), getOptimizationQuota()]);
            setPortfolios(updated);
            setPortfolioQuota(quota);
            setOptimizationQuota(optQuota);
            setActivePortfolioId(created.id);
            localStorage.setItem('activePortfolioId', String(created.id));
            markPortfolioReviewed(created.id);
            const bal = await getCashBalance(created.id).catch(() => null);
            setWizardCashBalance(bal);
            setWizardCashAmount('');
            setWizardStep('ai-cash');
        } catch (err: unknown) {
            const data = (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data;
            if (data?.error === 'PORTFOLIO_LIMIT_REACHED') {
                closeWizard();
                setUpgradeModal({ message: data.message ?? 'Portfolio limit reached.' });
            } else {
                setWizardError(data?.message || 'Failed to create portfolio. Please try again.');
            }
        } finally {
            setWizardSaving(false);
        }
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
            setOptimizeKey(k => k + 1);
            setRefreshKey(k => k + 1);
        } catch (err: unknown) {
            const data = (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data;
            closeWizard();
            if (data?.error === 'OPTIMIZATION_LIMIT_REACHED') {
                setUpgradeModal({ message: data.message ?? 'Optimization limit reached.' });
            } else {
                setActiveTab('optimize');
                setWizardError('AI optimization failed. You can try again from the AI Optimize tab.');
            }
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
            const [updated, quota, optQuota] = await Promise.all([listPortfolios(), getPortfolioQuota(), getOptimizationQuota()]);
            setPortfolios(updated);
            setPortfolioQuota(quota);
            setOptimizationQuota(optQuota);
            if (updated.length > 0) {
                const firstEnabled = updated.find(p => p.enabled);
                if (firstEnabled) {
                    setActivePortfolioId(firstEnabled.id);
                    localStorage.setItem('activePortfolioId', String(firstEnabled.id));
                } else {
                    setActivePortfolioId(updated[0].id);
                }
            } else {
                setActivePortfolioId(null);
            }
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
                <div className="navbar-brand" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome" style={{ fontSize: '0.75rem', marginTop: '-0.2rem', opacity: 0.8 }}>Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/getting-started" className="btn-logout">Getting Started</Link>
                    <Link to="/account" className="btn-logout">Account</Link>
                    <Link to="/profile" className="btn-logout">Default Profile</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    <Link to="/support" className="btn-logout">Support</Link>
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
                <div className="portfolio-header-row" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
                    <div>
                        <h1 className="portfolio-heading">My Portfolio</h1>
                        <p className="portfolio-sub">Prices updated at 9am &amp; 3pm ET on market days.</p>
                    </div>
                    <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'stretch' }}>
                        {portfolioQuota && (
                            <div style={{ textAlign: 'right', background: 'var(--bg-card)', padding: '0.5rem 1rem', borderRadius: 8, border: '1px solid var(--border)', minWidth: 120 }}>
                                <div style={{ fontSize: '0.72rem', color: 'var(--text-gray)', marginBottom: 2 }}>Portfolios</div>
                                <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                                    {portfolioQuota.used} <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--text-gray)' }}>/ {portfolioQuota.max}</span>
                                </div>
                                <div style={{ fontSize: '0.65rem', color: portfolioQuota.used >= portfolioQuota.max ? '#ef4444' : '#22c55e', marginTop: 2 }}>
                                    {Math.max(0, portfolioQuota.max - portfolioQuota.used)} left
                                </div>
                            </div>
                        )}
                        {optimizationQuota && (
                            <div style={{ textAlign: 'right', background: 'var(--bg-card)', padding: '0.5rem 1rem', borderRadius: 8, border: '1px solid var(--border)', minWidth: 120 }}>
                                <div style={{ fontSize: '0.72rem', color: 'var(--text-gray)', marginBottom: 2 }}>AI Optimizations</div>
                                <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                                    {optimizationQuota.used} <span style={{ fontSize: '0.8rem', fontWeight: 400, color: 'var(--text-gray)' }}>/ {optimizationQuota.max}</span>
                                </div>
                                <div style={{ fontSize: '0.65rem', color: (optimizationQuota.used + optimizationQuota.scheduled) >= optimizationQuota.max ? '#ef4444' : '#22c55e', marginTop: 2 }}>
                                    {Math.max(0, optimizationQuota.max - optimizationQuota.used - optimizationQuota.scheduled)} left
                                </div>
                            </div>
                        )}
                        {((portfolioQuota && portfolioQuota.used >= portfolioQuota.max) || 
                          (optimizationQuota && (optimizationQuota.used + optimizationQuota.scheduled) >= optimizationQuota.max)) && (
                            <Link to="/subscription" style={{
                                background: '#6c47ff',
                                color: '#fff',
                                textDecoration: 'none',
                                padding: '0 1.25rem',
                                borderRadius: 8,
                                display: 'flex',
                                alignItems: 'center',
                                fontWeight: 700,
                                fontSize: '0.9rem',
                                boxShadow: '0 4px 12px rgba(108, 71, 255, 0.3)'
                            }}>
                                Upgrade
                            </Link>
                        )}
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
                                    const p = portfolios.find(x => x.id === id);
                                    if (p && !p.enabled) {
                                        setUpgradeModal({ message: `Portfolio "${p.name}" is disabled because it exceeds your current plan limits. Please upgrade to reactivate it.` });
                                        return;
                                    }
                                    setActivePortfolioId(id);
                                    localStorage.setItem('activePortfolioId', String(id));
                                    setRefreshKey(k => k + 1);
                                    setAiRunTimestamps([]);
                                    setAiRunsLoaded(false);
                                    setAiRunsPage(0);
                                    setExpandedRunDetails({});
                                    setExpandedRunLoading({});
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
                                    <option key={p.id} value={p.id} style={{ background: '#1e2030', color: p.enabled ? '#e2e8f0' : '#888' }}>
                                        {p.name} {!p.enabled ? '(Disabled - Upgrade to access)' : ''}
                                    </option>
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
                    <button
                        className="btn-logout"
                        style={{ fontSize: '0.85rem', padding: '0.35rem 0.7rem' }}
                        onClick={openWizard}
                    >
                        + New Portfolio
                    </button>
                    {portfolios.length > 0 && (
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
                        {portfolios.length > 0 ? (
                            <>
                                <p style={{ fontSize: '1.1rem', color: 'var(--text-primary)', marginBottom: '0.5rem' }}>All portfolios are currently disabled.</p>
                                <p style={{ marginBottom: '1.5rem' }}>Your current plan limits have been exceeded. Please upgrade to reactivate your portfolios.</p>
                                <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
                                    <Link to="/subscription" className="btn-primary-full" style={{ maxWidth: 240, textDecoration: 'none' }}>
                                        Upgrade Plan
                                    </Link>
                                    <Link to="/getting-started" className="btn-logout" style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', padding: '0.6rem 1.2rem', borderRadius: 8 }}>
                                        View Getting Started Guide
                                    </Link>
                                </div>
                            </>
                        ) : (
                            <>
                                <p>No portfolio found. Create your first portfolio to get started.</p>
                                <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '1rem' }}>
                                    <button className="btn-primary-full" style={{ maxWidth: 240 }}
                                        onClick={openWizard}>
                                        Create Portfolio
                                    </button>
                                    <Link to="/getting-started" className="btn-logout" style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', padding: '0.6rem 1.2rem', borderRadius: 8 }}>
                                        View Getting Started Guide
                                    </Link>
                                </div>
                            </>
                        )}
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
                                className={`tab-btn ${activeTab === 'aiOptimizations' ? 'tab-active' : ''}`}
                                onClick={() => {
                                    setActiveTab('aiOptimizations');
                                    setProfileBannerMsg('');
                                    if (activePortfolioId && !aiRunsLoaded) {
                                        setAiRunsLoading(true);
                                        setAiRunsError('');
                                        getAiRunTimestamps(activePortfolioId)
                                            .then(ts => { setAiRunTimestamps(ts); setAiRunsLoaded(true); })
                                            .catch(() => setAiRunsError('Failed to load AI optimization runs.'))
                                            .finally(() => setAiRunsLoading(false));
                                    }
                                }}
                            >
                                AI Optimizations
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
                            <PortfolioDashboard key={`holdings-${activePortfolioId}-${refreshKey}`} portfolioId={activePortfolioId} onTradeSuccess={handleTradeSuccess} />
                        )}
                        {activeTab === 'transactions' && (
                            <TransactionHistory key={`tx-${activePortfolioId}-${refreshKey}`} portfolioId={activePortfolioId} />
                        )}
                        {activeTab === 'aiOptimizations' && (
                            <div style={{ marginTop: '1rem' }}>
                                {aiRunsLoading && (
                                    <div style={{ textAlign: 'center', color: 'var(--text-gray)', padding: '2rem' }}>Loading AI runs…</div>
                                )}
                                {!aiRunsLoading && aiRunsError && (
                                    <div style={{ textAlign: 'center', color: '#f87171', padding: '2rem' }}>{aiRunsError}</div>
                                )}
                                {!aiRunsLoading && !aiRunsError && aiRunsLoaded && aiRunTimestamps.length === 0 && (
                                    <div style={{ textAlign: 'center', color: 'var(--text-gray)', padding: '2rem' }}>No AI optimization runs found.</div>
                                )}
                                {!aiRunsLoading && aiRunTimestamps.length > 0 && (() => {
                                    const totalPages = Math.ceil(aiRunTimestamps.length / AI_RUNS_PAGE_SIZE);
                                    const pageTs = aiRunTimestamps.slice(aiRunsPage * AI_RUNS_PAGE_SIZE, (aiRunsPage + 1) * AI_RUNS_PAGE_SIZE);
                                    const aiBadgeLabel = (provider: string | null) => {
                                        const p = provider?.toLowerCase();
                                        if (p === 'claude') return 'Claude';
                                        if (p === 'chatgpt') return 'ChatGPT';
                                        if (p === 'gemini') return 'Gemini';
                                        return 'AI';
                                    };
                                    const fmt = (n: number | null) =>
                                        n != null ? `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';
                                    return (
                                        <>
                                            {pageTs.map(ts => {
                                                const details = expandedRunDetails[ts];
                                                const isLoading = expandedRunLoading[ts];
                                                const isExpanded = !!details;
                                                const profile = details?.profile;
                                                return (
                                                    <div key={ts} style={{ border: '1px solid var(--border)', borderRadius: 8, marginBottom: '1rem', overflow: 'hidden' }}>
                                                        <div
                                                            onClick={() => {
                                                                if (isExpanded) {
                                                                    setExpandedRunDetails(prev => { const n = { ...prev }; delete n[ts]; return n; });
                                                                } else if (!isLoading && activePortfolioId) {
                                                                    setExpandedRunLoading(prev => ({ ...prev, [ts]: true }));
                                                                    getAiRunDetails(activePortfolioId, ts)
                                                                        .then(d => setExpandedRunDetails(prev => ({ ...prev, [ts]: d })))
                                                                        .finally(() => setExpandedRunLoading(prev => { const n = { ...prev }; delete n[ts]; return n; }));
                                                                }
                                                            }}
                                                            style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem 1rem', cursor: 'pointer', background: 'var(--bg-card)', userSelect: 'none' }}
                                                        >
                                                            <span style={{ fontWeight: 600, fontSize: '0.88rem' }}>
                                                                {new Date(ts).toLocaleString()}
                                                            </span>
                                                            {details?.recommendations[0]?.aiProvider && (
                                                                <span style={{ background: 'var(--bg-dark)', border: '1px solid var(--border)', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 600 }}>
                                                                    via {aiBadgeLabel(details.recommendations[0].aiProvider)}
                                                                </span>
                                                            )}
                                                            {details?.scheduleFrequency && (
                                                                <span style={{ background: 'rgba(34,197,94,0.12)', border: '1px solid rgba(34,197,94,0.4)', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.8rem', color: '#22c55e', fontWeight: 600 }}>
                                                                    🗓 {details.scheduleFrequency.charAt(0) + details.scheduleFrequency.slice(1).toLowerCase()}
                                                                </span>
                                                            )}
                                                            {details?.confidenceScore != null && (
                                                                <span style={{ background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.4)', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.8rem', color: '#a78bfa', fontWeight: 600 }}>
                                                                    🎯 {details.confidenceScore}%
                                                                </span>
                                                            )}
                                                            <span style={{ marginLeft: 'auto', color: 'var(--text-gray)', fontSize: '0.8rem' }}>
                                                                {isLoading ? 'Loading…' : isExpanded ? '▲' : '▼'}
                                                            </span>
                                                        </div>
                                                        {isExpanded && (
                                                            <div style={{ padding: '1rem' }}>
                                                                {profile && (
                                                                    <>
                                                                        <h3 style={{ color: 'var(--text-light)', marginBottom: '0.75rem', fontSize: '0.95rem' }}>
                                                                            Portfolio Profile used for this optimization
                                                                        </h3>
                                                                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem 1.5rem', background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem', fontSize: '0.88rem', marginBottom: '1.5rem' }}>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Risk Tolerance: </span><strong>{profile.riskLevel?.replace('_', ' ') ?? 'N/A'}</strong></div>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Primary Goal: </span><strong>{profile.goal ?? 'N/A'}</strong></div>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Time Horizon: </span><strong>{profile.horizonYears != null ? `${profile.horizonYears} years` : 'N/A'}</strong></div>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Liquidity Needs: </span><strong>{profile.liquidityNeeds ?? 'N/A'}</strong></div>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Currency: </span><strong>{profile.currency}</strong></div>
                                                                            <div><span style={{ color: 'var(--text-gray)' }}>Preferred Sectors: </span><strong>{profile.sectorConstraints?.length ? profile.sectorConstraints.join(', ') : 'None'}</strong></div>
                                                                            {profile.taxOptimization && (
                                                                                <div><span style={{ color: 'var(--text-gray)' }}>Tax Optimization: </span><strong style={{ color: '#22c55e' }}>Enabled</strong></div>
                                                                            )}
                                                                            <div style={{ gridColumn: '1 / -1' }}>
                                                                                <span style={{ color: 'var(--text-gray)' }}>Additional Notes: </span>
                                                                                <span>{profile.additionalComments ?? 'None'}</span>
                                                                            </div>
                                                                        </div>
                                                                    </>
                                                                )}
                                                                <h3 style={{ color: 'var(--text-light)', marginBottom: '0.5rem', fontSize: '0.95rem' }}>Trades in this run</h3>
                                                                <div className="holdings-table-wrap" style={{ marginBottom: '0.5rem' }}>
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
                                                                            {details.recommendations.map(rec => (
                                                                                <tr key={rec.id}>
                                                                                    <td className={rec.action === 'BUY' ? 'positive' : 'negative'} style={{ fontWeight: 600 }}>{rec.action}</td>
                                                                                    <td className="symbol-cell">{rec.t}</td>
                                                                                    <td style={{ fontSize: '0.82rem', color: 'var(--text-gray)' }}>{rec.n}</td>
                                                                                    <td>{rec.w.toFixed(1)}%</td>
                                                                                    <td>{fmt(rec.estimatedValue)}</td>
                                                                                    <td style={{
                                                                                        color: rec.status === 'EXECUTED' ? '#22c55e' : rec.status === 'SKIPPED' ? '#f59e0b' : 'var(--text-gray)',
                                                                                        fontWeight: 600, fontSize: '0.82rem',
                                                                                    }}>{rec.status}</td>
                                                                                    <td style={{ fontSize: '0.78rem', color: 'var(--text-gray)', maxWidth: 200 }}>{rec.r}</td>
                                                                                </tr>
                                                                            ))}
                                                                        </tbody>
                                                                    </table>
                                                                </div>
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })}
                                            {totalPages > 1 && (
                                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem', marginTop: '0.5rem' }}>
                                                    <button
                                                        onClick={() => setAiRunsPage(p => Math.max(0, p - 1))}
                                                        disabled={aiRunsPage === 0}
                                                        style={{ padding: '0.3rem 0.75rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-dark)', color: 'var(--text-primary)', cursor: aiRunsPage === 0 ? 'not-allowed' : 'pointer', opacity: aiRunsPage === 0 ? 0.4 : 1 }}
                                                    >&#8592;</button>
                                                    <span style={{ fontSize: '0.85rem', color: 'var(--text-gray)' }}>
                                                        {aiRunsPage + 1} / {totalPages}
                                                    </span>
                                                    <button
                                                        onClick={() => setAiRunsPage(p => Math.min(totalPages - 1, p + 1))}
                                                        disabled={aiRunsPage === totalPages - 1}
                                                        style={{ padding: '0.3rem 0.75rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-dark)', color: 'var(--text-primary)', cursor: aiRunsPage === totalPages - 1 ? 'not-allowed' : 'pointer', opacity: aiRunsPage === totalPages - 1 ? 0.4 : 1 }}
                                                    >&#8594;</button>
                                                </div>
                                            )}
                                        </>
                                    );
                                })()}
                            </div>
                        )}
                        {activeTab === 'optimize' && (
                            <>
                                <OptimizePanel key={`opt-${activePortfolioId}-${optimizeKey}`} portfolioId={activePortfolioId} onTradeSuccess={handleTradeSuccess} onNavigateToProfile={() => setActiveTab('profile')} cashRefreshSignal={refreshKey} />
                                <ScheduleManager portfolioId={activePortfolioId} onUpgradeRequired={msg => setUpgradeModal({ message: msg })} />
                            </>
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
                            <button className="btn-primary-full" onClick={handleChooseAI} disabled={wizardQuotaChecking}>
                                {wizardQuotaChecking ? 'Checking…' : 'Yes, Use AI'}
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
                        <p style={{ color: 'var(--text-gray)', fontSize: '0.88rem', marginBottom: '1rem' }}>
                            Tell us about this portfolio so the AI can build your initial holdings.
                            Fields marked with <span style={{ color: '#f87171' }}>*</span> are required unless you fill in Additional Notes instead.
                        </p>

                        {wizardError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{wizardError}</div>}

                        <button className="btn-primary-full" onClick={handleWizardSaveProfile} disabled={wizardSaving} style={{ marginBottom: '1.5rem' }}>
                            {wizardSaving ? 'Saving…' : 'Save & Continue'}
                        </button>

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
                                <div style={{ display: 'flex', alignItems: 'center', marginBottom: '0.5rem' }}>
                                    <span style={{ padding: '0.55rem 0.6rem', background: 'var(--bg-sidebar)', border: '1px solid var(--border)', borderRight: 'none', borderRadius: '6px 0 0 6px', color: 'var(--text-gray)', fontSize: '0.95rem', whiteSpace: 'nowrap' }}>+1</span>
                                    <input
                                        type="text"
                                        value={wizardPhone}
                                        onChange={e => setWizardPhone(e.target.value)}
                                        placeholder="555-000-0000"
                                        style={{ borderRadius: '0 6px 6px 0', flex: 1 }}
                                        autoFocus
                                    />
                                </div>
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
                        {wizardError && <div className="error-msg" style={{ marginTop: '1rem', marginBottom: '0.5rem' }}>{wizardError}</div>}
                        <button className="btn-primary-full" style={{ marginTop: '0.75rem' }} onClick={handleWizardGoToCash} disabled={wizardSaving}>
                            {wizardSaving ? 'Creating Portfolio…' : 'Next'}
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

            {upgradeModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}
                    onClick={() => setUpgradeModal(null)}>
                    <div style={{ background: 'var(--bg-card)', borderRadius: 12, border: '1px solid var(--border)', padding: '2rem', width: '100%', maxWidth: 440, margin: '1rem', boxShadow: '0 20px 60px rgba(0,0,0,0.5)' }}
                        onClick={e => e.stopPropagation()}>
                        <div style={{ fontSize: '2rem', textAlign: 'center', marginBottom: '0.75rem' }}>🔒</div>
                        <h2 style={{ textAlign: 'center', fontWeight: 700, fontSize: '1.1rem', color: 'var(--text-primary)', marginBottom: '0.75rem' }}>Upgrade Required</h2>
                        <p style={{ textAlign: 'center', fontSize: '0.9rem', color: 'var(--text-gray)', marginBottom: '1.5rem', lineHeight: 1.6 }}>{upgradeModal.message}</p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                            <Link to="/subscription"
                                style={{ display: 'block', textAlign: 'center', background: '#6c47ff', color: '#fff', borderRadius: 7, padding: '0.6rem 1rem', fontWeight: 700, fontSize: '0.95rem', textDecoration: 'none' }}
                                onClick={() => setUpgradeModal(null)}>
                                View Subscription Plans
                            </Link>
                            <button onClick={() => setUpgradeModal(null)}
                                style={{ background: 'transparent', color: 'var(--text-gray)', border: '1px solid var(--border)', borderRadius: 7, padding: '0.5rem 1rem', cursor: 'pointer', fontSize: '0.9rem' }}>
                                Not Now
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Portfolio;
