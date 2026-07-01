import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { optimizePortfolio, getProfile } from '../api/profileApi';
import type { Recommendation, UserProfile } from '../api/profileApi';
import { getAccountProfile, sendEmailVerification, verifyEmail } from '../api/accountApi';
import { isEmailVerified } from '../utils/auth';
import { getQuote } from '../api/marketApi';
import {
    submitTransaction,
    getCashBalance,
    getHoldings,
    getTodayRecommendations,
    markRecommendationExecuted,
    markRecommendationSkipped,
} from '../api/portfolioApi';

interface ReadinessIssue {
    message: string;
    link: string;
    label: string;
}

interface Props {
    portfolioId: number;
    onTradeSuccess?: () => void;
    onNavigateToProfile?: () => void;
    cashRefreshSignal?: number;
}

function checkReadiness(
    profile: Awaited<ReturnType<typeof getProfile>>,
    account: Awaited<ReturnType<typeof getAccountProfile>> | null,
): ReadinessIssue[] {
    const issues: ReadinessIssue[] = [];
    if (!profile || !profile.horizonYears) {
        issues.push({
            message: 'Complete your Investor Profile (time horizon is required).',
            link: '/profile',
            label: 'Investor Profile',
        });
    }
    const acctRequired = ['firstName', 'lastName', 'addressLine1', 'city', 'state', 'postalCode', 'country'] as const;
    const acctMissing = account == null || acctRequired.some(f => !account[f]?.trim());
    if (acctMissing) {
        issues.push({
            message: 'Complete your Account Maintenance page (name and address are required).',
            link: '/account',
            label: 'Account Maintenance',
        });
    }
    return issues;
}

const fmt = (n: number | null) =>
    n != null ? `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : '—';

type AiProvider = 'gemini' | 'claude' | 'chatgpt';

const PROVIDER_LABELS: Record<AiProvider, string> = {
    gemini: 'Gemini',
    claude: 'Claude',
    chatgpt: 'ChatGPT',
};

const OptimizePanel: React.FC<Props> = ({ portfolioId, onTradeSuccess, onNavigateToProfile, cashRefreshSignal }) => {
    const [loading, setLoading] = useState(false);
    const [checking, setChecking] = useState(true);
    const [readinessIssues, setReadinessIssues] = useState<ReadinessIssue[]>([]);
    const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
    const [error, setError] = useState('');
    const [ran, setRan] = useState(false);
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [cashBalance, setCashBalance] = useState<number>(0);
    const [holdingsMarketValue, setHoldingsMarketValue] = useState<number>(0);
    const [checkedIds, setCheckedIds] = useState<Set<number>>(new Set());
    const [executing, setExecuting] = useState(false);
    const [execMsg, setExecMsg] = useState<{ text: string; ok: boolean } | null>(null);
    const [showSkipConfirm, setShowSkipConfirm] = useState<'sellAll' | 'sellChecked' | null>(null);
    const [pendingBuyAction, setPendingBuyAction] = useState<'all' | 'checked' | null>(null);
    const [aiProvider, setAiProvider] = useState<AiProvider | null>(null);

    const [userEmail, setUserEmail] = useState<string>('');
    const [showVerifyModal, setShowVerifyModal] = useState(false);
    const [verifyEmail2, setVerifyEmail2] = useState<string>('');
    const [verifyStep, setVerifyStep] = useState<'email' | 'code'>('email');
    const [verifyCode, setVerifyCode] = useState('');
    const [verifyLoading, setVerifyLoading] = useState(false);
    const [verifyError, setVerifyError] = useState('');
    const [pendingOptimize, setPendingOptimize] = useState(false);

    const loadCash = () => getCashBalance(portfolioId).then(c => {
        setCashBalance(c);
        setError(prev => prev.includes('Insufficient funds') ? '' : prev);
        return c;
    }).catch(() => 0);

    const loadHoldingsValue = () => getHoldings(portfolioId)
        .then(h => { setHoldingsMarketValue(h.reduce((s, x) => s + (x.marketValue ?? 0), 0)); })
        .catch(() => {});

    useEffect(() => {
        if (cashRefreshSignal === undefined || cashRefreshSignal === 0) return;
        loadCash();
        loadHoldingsValue();
    }, [cashRefreshSignal]);

    useEffect(() => {
        loadHoldingsValue();
        Promise.all([
            getProfile().catch(() => null),
            getAccountProfile().catch(() => null),
            loadCash(),
            getTodayRecommendations(portfolioId).catch(() => []),
        ]).then(([prof, account, , recs]) => {
            setProfile(prof);
            setReadinessIssues(checkReadiness(prof, account));
            if (account?.email) {
                setUserEmail(account.email);
                setVerifyEmail2(account.email);
            }
            if (recs && recs.length > 0) {
                setRecommendations(recs);
                setRan(true);
            }
        }).finally(() => setChecking(false));
    }, []);

    const openVerifyModal = () => {
        setVerifyStep('email');
        setVerifyCode('');
        setVerifyError('');
        setShowVerifyModal(true);
    };

    const handleSendVerificationCode = async () => {
        setVerifyLoading(true);
        setVerifyError('');
        try {
            const resp = await sendEmailVerification(verifyEmail2 !== userEmail ? verifyEmail2 : undefined);
            if (resp.token) {
                localStorage.setItem('token', resp.token);
                setUserEmail(verifyEmail2);
            }
            setVerifyStep('code');
        } catch (e: unknown) {
            const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setVerifyError(msg ?? 'Failed to send verification code.');
        } finally {
            setVerifyLoading(false);
        }
    };

    const handleVerifyCode = async () => {
        if (!verifyCode.trim()) { setVerifyError('Please enter the verification code.'); return; }
        setVerifyLoading(true);
        setVerifyError('');
        try {
            const resp = await verifyEmail(verifyCode.trim());
            if (resp.token) {
                localStorage.setItem('token', resp.token);
            }
            setShowVerifyModal(false);
            setPendingOptimize(true);
        } catch (e: unknown) {
            const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setVerifyError(msg ?? 'Invalid or expired code.');
        } finally {
            setVerifyLoading(false);
        }
    };

    useEffect(() => {
        if (pendingOptimize) {
            setPendingOptimize(false);
            runOptimize();
        }
    }, [pendingOptimize]);

    const handleOptimize = () => {
        if (!isEmailVerified()) {
            openVerifyModal();
            return;
        }
        runOptimize();
    };

    const runOptimize = async () => {
        setLoading(true);
        setError('');
        setRan(false);
        setCheckedIds(new Set());
        setExecMsg(null);
        try {
            const [holdings, cash] = await Promise.all([
                getHoldings(portfolioId).catch(() => []),
                loadCash(),
            ]);
            const holdingsValue = holdings.reduce((sum, h) => sum + (h.marketValue ?? 0), 0);
            const totalAvailable = holdingsValue + cash;
            if (totalAvailable < 1000) {
                setError(`Insufficient funds: total portfolio value is ${fmt(totalAvailable)}. At least $1,000 in cash or holdings is required to run optimization.`);
                setLoading(false);
                return;
            }
            if (!aiProvider) {
                setError('Please select an AI model before generating recommendations.');
                setLoading(false);
                return;
            }
            const result = await optimizePortfolio(portfolioId, aiProvider);
            if (result.error) {
                setError(result.error);
                setRecommendations([]);
            } else {
                setRecommendations(result.recommendations);
            }
            setRan(true);
            await Promise.all([loadCash(), loadHoldingsValue()]);
        } catch (e: unknown) {
            setError(e instanceof Error ? e.message : 'Optimization request failed.');
            setRecommendations([]);
        } finally {
            setLoading(false);
        }
    };

    const toggleChecked = (id: number) => {
        setCheckedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    };

    const updateRec = (updated: Recommendation) =>
        setRecommendations(prev => prev.map(r => r.id === updated.id ? updated : r));

    const executeSells = async (targets: Recommendation[]) => {
        if (targets.length === 0) return { succeeded: 0, failed: [] as string[] };
        setExecuting(true);
        const failed: string[] = [];
        let succeeded = 0;

        let holdingsMap = new Map<string, number>();
        try {
            const h = await getHoldings(portfolioId);
            h.forEach(h => holdingsMap.set(h.symbol, h.quantity));
        } catch {
            failed.push('Could not fetch holdings for sell quantities');
        }

        for (const rec of targets) {
            const qty = holdingsMap.get(rec.t);
            if (!qty || qty <= 0) { failed.push(`${rec.t}: no position found`); continue; }
            try {
                const quote = await getQuote(rec.t);
                const tx = await submitTransaction({ symbol: rec.t, type: 'SELL', quantity: qty, price: quote.price }, portfolioId);
                const actualProceeds = tx.quantity * tx.price;
                await markRecommendationExecuted(rec.id, tx.id, portfolioId, actualProceeds);
                updateRec({ ...rec, status: 'EXECUTED', transactionId: tx.id, estimatedValue: actualProceeds });
                succeeded++;
            } catch (e) {
                failed.push(`SELL ${rec.t}: ${e instanceof Error ? e.message : String(e)}`);
            }
        }
        return { succeeded, failed };
    };

    const executeBuys = async (targets: Recommendation[], cash: number) => {
        if (targets.length === 0 || cash <= 0) return { succeeded: 0, failed: [] as string[] };
        const failed: string[] = [];

        const results = await Promise.allSettled(targets.map(async rec => {
            const quote = await getQuote(rec.t);
            const allocation = cash * (rec.w / 100);
            const quantity = Math.floor((allocation / quote.price) * 100) / 100;
            if (quantity <= 0) throw new Error(`${rec.t}: allocation too small`);
            const tx = await submitTransaction({ symbol: rec.t, type: 'BUY', quantity, price: quote.price }, portfolioId);
            const actualAmount = tx.quantity * tx.price;
            await markRecommendationExecuted(rec.id, tx.id, portfolioId, actualAmount);
            updateRec({ ...rec, status: 'EXECUTED', transactionId: tx.id, estimatedValue: actualAmount });
            return rec.t;
        }));

        const succeeded = results.filter(r => r.status === 'fulfilled').length;
        results.filter((r): r is PromiseRejectedResult => r.status === 'rejected')
            .forEach(r => failed.push(`BUY: ${r.reason instanceof Error ? r.reason.message : String(r.reason)}`));
        return { succeeded, failed };
    };

    const handleSells = async (targets: Recommendation[]) => {
        setExecMsg(null);
        setExecuting(true);
        const { succeeded, failed } = await executeSells(targets);
        const [newCash] = await Promise.all([loadCash(), loadHoldingsValue()]);
        setExecuting(false);
        setExecMsg({
            ok: failed.length === 0,
            text: `${succeeded} sell(s) executed. Cash refreshed to ${fmt(newCash)}.${failed.length ? ' Failures: ' + failed.join(', ') : ''}`,
        });
        setRecommendations(prev => prev.map(r =>
            r.action === 'BUY' && r.status === 'PENDING'
                ? { ...r, estimatedValue: newCash * r.w / 100 }
                : r
        ));
        if (succeeded > 0) onTradeSuccess?.();
    };

    const handleBuys = async (targets: Recommendation[]) => {
        setExecMsg(null);
        setExecuting(true);
        const cash = await loadCash();
        const { succeeded, failed } = await executeBuys(targets, cash);
        await loadCash();
        setExecuting(false);
        if (succeeded > 0) onTradeSuccess?.();
        setExecMsg({
            ok: failed.length === 0,
            text: `${succeeded} buy(s) executed.${failed.length ? ' Failures: ' + failed.join(', ') : ''}`,
        });
    };

    const pendingSells = recommendations.filter(r => r.action === 'SELL' && r.status === 'PENDING');
    const pendingBuys = recommendations.filter(r => r.action === 'BUY' && r.status === 'PENDING');
    const checkedSells = pendingSells.filter(r => checkedIds.has(r.id));
    const checkedBuys = pendingBuys.filter(r => checkedIds.has(r.id));

    const onSellAll = () => handleSells(pendingSells);
    const onSellChecked = () => handleSells(checkedSells);

    const onBuyAll = () => {
        if (pendingSells.length > 0) { setPendingBuyAction('all'); setShowSkipConfirm('sellAll'); }
        else handleBuys(pendingBuys);
    };
    const onBuyChecked = () => {
        if (pendingSells.length > 0) { setPendingBuyAction('checked'); setShowSkipConfirm('sellChecked'); }
        else handleBuys(checkedBuys);
    };

    const handleSkipSellsConfirmed = async () => {
        for (const r of pendingSells) {
            await markRecommendationSkipped(r.id, portfolioId).catch(() => {});
            updateRec({ ...r, status: 'SKIPPED' });
        }
        setShowSkipConfirm(null);
        const action = pendingBuyAction;
        setPendingBuyAction(null);
        if (action === 'all') handleBuys(pendingBuys);
        else handleBuys(checkedBuys);
    };

    const sells = recommendations.filter(r => r.action === 'SELL');
    const buys = recommendations.filter(r => r.action === 'BUY');
    const buyWeight = buys.filter(r => r.status === 'PENDING').reduce((s, r) => s + r.w, 0);
    const isBlocked = readinessIssues.length > 0;

    return (
        <div className="optimize-panel">
            {showVerifyModal && (
                <div className="modal-overlay" onClick={() => { if (!verifyLoading) setShowVerifyModal(false); }}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 460 }}>
                        <div className="modal-header">
                            <h2>Verify Your Email</h2>
                            <button className="modal-close" onClick={() => setShowVerifyModal(false)} disabled={verifyLoading}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem 1.5rem' }}>
                            {verifyStep === 'email' ? (
                                <>
                                    <p style={{ marginBottom: '1rem', fontSize: '0.9rem', color: 'var(--text-gray)' }}>
                                        To run AI optimization for the first time, we need to verify your email address. You can update it below before sending the code.
                                    </p>
                                    <label style={{ display: 'block', marginBottom: '0.4rem', fontSize: '0.85rem', fontWeight: 600 }}>Email Address</label>
                                    <input
                                        type="email"
                                        value={verifyEmail2}
                                        onChange={e => setVerifyEmail2(e.target.value)}
                                        style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text)', fontSize: '0.95rem', boxSizing: 'border-box' }}
                                        disabled={verifyLoading}
                                        autoFocus
                                    />
                                    {verifyError && <p style={{ color: '#ef4444', fontSize: '0.85rem', marginTop: '0.5rem' }}>{verifyError}</p>}
                                    <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', justifyContent: 'flex-end' }}>
                                        <button onClick={() => setShowVerifyModal(false)} disabled={verifyLoading} style={{ padding: '0.5rem 1rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text)', cursor: 'pointer', fontSize: '0.9rem' }}>Cancel</button>
                                        <button className="btn-trade" onClick={handleSendVerificationCode} disabled={verifyLoading || !verifyEmail2.trim()}>
                                            {verifyLoading ? 'Sending…' : 'Send Code'}
                                        </button>
                                    </div>
                                </>
                            ) : (
                                <>
                                    <p style={{ marginBottom: '1rem', fontSize: '0.9rem', color: 'var(--text-gray)' }}>
                                        A 6-digit verification code was sent to <strong>{verifyEmail2}</strong>. Enter it below.
                                    </p>
                                    <label style={{ display: 'block', marginBottom: '0.4rem', fontSize: '0.85rem', fontWeight: 600 }}>Verification Code</label>
                                    <input
                                        type="text"
                                        value={verifyCode}
                                        onChange={e => setVerifyCode(e.target.value)}
                                        placeholder="000000"
                                        maxLength={6}
                                        style={{ width: '100%', padding: '0.6rem 0.75rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text)', fontSize: '1.1rem', letterSpacing: '0.2em', textAlign: 'center', boxSizing: 'border-box' }}
                                        disabled={verifyLoading}
                                        autoFocus
                                    />
                                    {verifyError && <p style={{ color: '#ef4444', fontSize: '0.85rem', marginTop: '0.5rem' }}>{verifyError}</p>}
                                    <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                                        <button onClick={() => setVerifyStep('email')} disabled={verifyLoading} style={{ padding: '0.5rem 1rem', borderRadius: 6, border: '1px solid var(--border)', background: 'var(--bg-card)', color: 'var(--text)', cursor: 'pointer', fontSize: '0.9rem' }}>← Change Email</button>
                                        <button className="btn-trade" onClick={handleVerifyCode} disabled={verifyLoading || verifyCode.length < 6}>
                                            {verifyLoading ? 'Verifying…' : 'Verify & Continue'}
                                        </button>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {showSkipConfirm && (
                <div className="modal-overlay" onClick={() => setShowSkipConfirm(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 420 }}>
                        <div className="modal-header">
                            <h2>Skip Recommended Sells?</h2>
                            <button className="modal-close" onClick={() => setShowSkipConfirm(null)}>✕</button>
                        </div>
                        <p style={{ padding: '1rem 1.5rem', color: 'var(--text-gray)', fontSize: '0.9rem' }}>
                            There are <strong>{pendingSells.length}</strong> recommended sell(s) you have not yet executed.
                            Skipping them means your buys will use only your current cash balance of <strong>{fmt(cashBalance)}</strong>.
                            <br /><br />
                            Are you sure you want to proceed to buys without selling?
                        </p>
                        <div style={{ display: 'flex', gap: '0.75rem', padding: '0 1.5rem 1.5rem' }}>
                            <button className="btn-primary-full" style={{ background: '#ef4444' }} onClick={handleSkipSellsConfirmed}>
                                Yes, skip sells and proceed to buys
                            </button>
                            <button className="btn-primary-full" style={{ background: 'var(--bg-card)' }} onClick={() => setShowSkipConfirm(null)}>
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            )}

            <div className="optimize-header">
                <div>
                    <h2 className="optimize-title">AI Portfolio Optimizer</h2>
                    <p className="optimize-sub">
                        Rebalances your portfolio based on your{' '}
                        <button
                            className="optimize-link"
                            onClick={() => onNavigateToProfile?.()}
                            style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', font: 'inherit' }}
                        >portfolio profile</button>.
                        {ran && recommendations.length > 0 && (
                            <span style={{ marginLeft: '0.5rem', fontSize: '0.78rem', color: 'var(--text-gray)' }}>
                                Generated {new Date(recommendations[0].generatedAt).toLocaleString('en-US', { month: 'numeric', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })}
                            </span>
                        )}
                    </p>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginTop: '0.5rem' }}>
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-gray)' }}>AI Model:</span>
                        {(['gemini', 'claude', 'chatgpt'] as AiProvider[]).map(p => (
                            <label key={p} style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', cursor: 'pointer', fontSize: '0.85rem' }}>
                                <input
                                    type="radio"
                                    name="aiProvider"
                                    value={p}
                                    checked={aiProvider === p}
                                    onChange={() => setAiProvider(p)}
                                    disabled={loading}
                                />
                                {PROVIDER_LABELS[p]}
                            </label>
                        ))}
                    </div>
                </div>
                <button
                    className="btn-optimize"
                    onClick={handleOptimize}
                    disabled={loading || checking || isBlocked}
                    title={isBlocked ? 'Complete required profile information first' : undefined}
                >
                    {loading ? 'Analyzing...' : 'Generate Recommendations'}
                </button>
            </div>

            {!checking && (
                <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', margin: '1rem 0', padding: '0.75rem 1rem', background: 'var(--bg-card)', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <div>
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-gray)', display: 'block' }}>Available Cash</span>
                        <strong style={{ fontSize: '1rem' }}>{fmt(cashBalance)}</strong>
                    </div>
                    <div style={{ borderLeft: '1px solid var(--border)', paddingLeft: '1.5rem' }}>
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-gray)', display: 'block' }}>Securities Value</span>
                        <strong style={{ fontSize: '1rem' }}>{fmt(holdingsMarketValue)}</strong>
                    </div>
                    <div style={{ borderLeft: '1px solid var(--border)', paddingLeft: '1.5rem' }}>
                        <span style={{ fontSize: '0.78rem', color: 'var(--text-gray)', display: 'block' }}>Total Portfolio Value</span>
                        <strong style={{ fontSize: '1rem' }}>{fmt(cashBalance + holdingsMarketValue)}</strong>
                    </div>
                </div>
            )}

            {checking && <div className="portfolio-loading">Checking profile…</div>}

            {!checking && isBlocked && (
                <div className="optimize-readiness-warn">
                    <strong>Before running the optimizer, please complete the following:</strong>
                    <ul className="optimize-readiness-list">
                        {readinessIssues.map((issue, i) => (
                            <li key={i}>{issue.message}{' '}
                                <Link to={issue.link} className="optimize-link">Go to {issue.label} →</Link>
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {loading && <div className="portfolio-loading">Consulting {aiProvider ? PROVIDER_LABELS[aiProvider] : 'AI'}…</div>}
            {error && !loading && <div className="error-msg">{error}</div>}
            {ran && !loading && !error && recommendations.length === 0 && (
                <div className="optimize-empty">No recommendations returned.</div>
            )}

            {recommendations.length > 0 && !loading && (
                <div style={{ marginTop: '1.5rem' }}>
                    {execMsg && (
                        <div className={`buy-all-status ${execMsg.ok ? 'buy-all-success' : 'buy-all-partial'}`} style={{ marginBottom: '1rem' }}>
                            {execMsg.text}
                        </div>
                    )}

                    {sells.length > 0 && (
                        <div className="holdings-table-wrap" style={{ marginBottom: '1.5rem' }}>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
                                <div className="optimize-section-title sell-section-title" style={{ flex: 1 }}>
                                    Recommended Sells — exit these positions to fund rebalancing
                                </div>
                                <div style={{ display: 'flex', gap: '0.5rem', padding: '0.25rem 0.75rem' }}>
                                    <button className="btn-buy-all btn-sell"
                                        onClick={onSellAll}
                                        disabled={executing || pendingSells.length === 0}
                                        title="Sell all pending positions at market price">
                                        {executing ? 'Executing…' : 'Sell All'}
                                    </button>
                                    <button className="btn-buy-all btn-sell"
                                        onClick={onSellChecked}
                                        disabled={executing || checkedSells.length === 0}
                                        title={`Sell ${checkedSells.length} checked position(s)`}>
                                        {executing ? 'Executing…' : `Sell Checked (${checkedSells.length})`}
                                    </button>
                                </div>
                            </div>
                            <table className="holdings-table">
                                <thead>
                                    <tr>
                                        <th className="optimize-buy-cell">Select</th>
                                        <th>Ticker</th>
                                        <th>Name</th>
                                        <th>Sector</th>
                                        <th>Portfolio %</th>
                                        <th>Est. / Actual Proceeds</th>
                                        <th>Rationale</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {sells.map(rec => (
                                        <tr key={rec.id} className={rec.status !== 'PENDING' ? 'executed-row' : 'sell-row'}>
                                            <td className="optimize-buy-cell">
                                                <input type="checkbox" className="buy-checkbox"
                                                    checked={checkedIds.has(rec.id)}
                                                    onChange={() => toggleChecked(rec.id)}
                                                    disabled={executing || rec.status !== 'PENDING'} />
                                            </td>
                                            <td className="symbol-cell">{rec.t}</td>
                                            <td>{rec.n}</td>
                                            <td>{rec.s}</td>
                                            <td>{rec.w.toFixed(1)}%</td>
                                            <td>
                                                {rec.status === 'EXECUTED'
                                                    ? <span title="Actual proceeds received">{fmt(rec.estimatedValue)}</span>
                                                    : <span style={{ color: 'var(--text-gray)', fontSize: '0.85em' }} title="Estimated at current market value">~{fmt(rec.estimatedValue)}</span>
                                                }
                                            </td>
                                            <td className="optimize-rationale">{rec.r}</td>
                                            <td><span className={`rec-status rec-${rec.status.toLowerCase()}`}>{rec.status}</span></td>
                                        </tr>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <td colSpan={8} style={{ paddingLeft: '1.25rem', color: 'var(--text-gray)', fontSize: '0.8rem' }}>
                                            {sells.length} position(s) to exit • Full quantity sold at market price
                                        </td>
                                    </tr>
                                </tfoot>
                            </table>
                        </div>
                    )}

                    {buys.length > 0 && (
                        <div className="holdings-table-wrap">
                            {cashBalance <= 0 && pendingSells.length === 0 && (
                                <div className="optimize-cash-warn">
                                    No cash available. Deposit cash or execute sells first.
                                </div>
                            )}
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.5rem' }}>
                                <div className="optimize-section-title buy-section-title" style={{ flex: 1 }}>
                                    Recommended Buys — allocated from available cash (including sell proceeds)
                                </div>
                                <div style={{ display: 'flex', gap: '0.5rem', padding: '0.25rem 0.75rem' }}>
                                    <button className="btn-buy-all"
                                        onClick={onBuyAll}
                                        disabled={executing || pendingBuys.length === 0}
                                        title="Buy all pending securities">
                                        {executing ? 'Executing…' : 'Buy All'}
                                    </button>
                                    <button className="btn-buy-all"
                                        onClick={onBuyChecked}
                                        disabled={executing || checkedBuys.length === 0}
                                        title={`Buy ${checkedBuys.length} checked securities`}>
                                        {executing ? 'Executing…' : `Buy Checked (${checkedBuys.length})`}
                                    </button>
                                </div>
                            </div>
                            <table className="holdings-table">
                                <thead>
                                    <tr>
                                        <th className="optimize-buy-cell">Select</th>
                                        <th>Ticker</th>
                                        <th>Name</th>
                                        <th>Sector</th>
                                        <th>Allocation %</th>
                                        <th>Est. / Actual Amount</th>
                                        <th>Rationale</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {buys.map(rec => (
                                        <tr key={rec.id} className={rec.status !== 'PENDING' ? 'executed-row' : ''}>
                                            <td className="optimize-buy-cell">
                                                <input type="checkbox" className="buy-checkbox"
                                                    checked={checkedIds.has(rec.id)}
                                                    onChange={() => toggleChecked(rec.id)}
                                                    disabled={executing || rec.status !== 'PENDING'} />
                                            </td>
                                            <td className="symbol-cell">{rec.t}</td>
                                            <td>{rec.n}</td>
                                            <td>{rec.s}</td>
                                            <td>{rec.w.toFixed(1)}%</td>
                                            <td>
                                                {rec.status === 'EXECUTED'
                                                    ? <span title="Actual amount spent">{fmt(rec.estimatedValue)}</span>
                                                    : <span style={{ color: 'var(--text-gray)', fontSize: '0.85em' }} title="Estimated based on current cash">~{fmt(rec.estimatedValue)}</span>
                                                }
                                            </td>
                                            <td className="optimize-rationale">{rec.r}</td>
                                            <td><span className={`rec-status rec-${rec.status.toLowerCase()}`}>{rec.status}</span></td>
                                        </tr>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <td colSpan={4} style={{ paddingLeft: '1.25rem', color: 'var(--text-gray)', fontSize: '0.8rem' }}>
                                            {buys.length} securities
                                        </td>
                                        <td style={{ fontWeight: 700, paddingLeft: '1.25rem' }}>
                                            {buyWeight.toFixed(1)}%
                                        </td>
                                        <td colSpan={3} />
                                    </tr>
                                </tfoot>
                            </table>
                        </div>
                    )}
                </div>
            )}

            {!ran && !loading && (
                <div className="optimize-placeholder">
                    <p>Click "Generate Recommendations" to get an AI-generated rebalancing plan based on your current portfolio and investor profile.</p>
                    <p className="optimize-hint">
                        The optimizer recommends what to <strong>sell</strong> and what to <strong>buy</strong>.
                        Execute sells first — the buy Est. Amounts will refresh automatically using updated cash.
                    </p>
                </div>
            )}
        </div>
    );
};

export default OptimizePanel;
