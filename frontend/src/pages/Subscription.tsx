import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import API_GATEWAY from '../api/apiBase';

interface Plan {
    planName: string;
    displayName: string;
    monthlyPrice: number;
    annualPrice: number;
    maxPortfolios: number;
    maxOptimizationsPerMonth: number;
    description: string;
}

interface SubscriptionStatus {
    planName: string;
    displayName: string;
    billingCycle: string | null;
    status: string;
    nextBillingDate: string | null;
    monthlyPrice: number;
    annualPrice: number;
    maxPortfolios: number;
    maxOptimizationsPerMonth: number;
    paymentMethod?: {
        cardholderName: string;
        cardLastFour: string;
        cardBrand: string;
        expiryMonth: number;
        expiryYear: number;
    };
}

const authHeader = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });
const username = () => localStorage.getItem('username') ?? '';

const Subscription: React.FC = () => {
    const navigate = useNavigate();
    const [plans, setPlans] = useState<Plan[]>([]);
    const [status, setStatus] = useState<SubscriptionStatus | null>(null);
    const [usageStats, setUsageStats] = useState<{ portfolioCount: number; optimizationsThisMonth: number } | null>(null);
    const [loading, setLoading] = useState(true);
    const [selectedPlan, setSelectedPlan] = useState<string | null>(null);
    const [billingCycle, setBillingCycle] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY');
    const [showPaymentForm, setShowPaymentForm] = useState(false);
    const [cardholderName, setCardholderName] = useState('');
    const [cardNumber, setCardNumber] = useState('');
    const [expiryMonth, setExpiryMonth] = useState('');
    const [expiryYear, setExpiryYear] = useState('');
    const [billingZip, setBillingZip] = useState('');
    const [cvv, setCvv] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    useEffect(() => {
        Promise.all([
            axios.get(`${API_GATEWAY}/api/v1/subscription/plans`),
            axios.get(`${API_GATEWAY}/api/v1/subscription/status`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/portfolios/usage-stats`, { headers: authHeader() }),
        ]).then(([plansRes, statusRes, usageRes]) => {
            setPlans(plansRes.data);
            setStatus(statusRes.data);
            setUsageStats(usageRes.data);
        }).catch(() => setError('Failed to load subscription info.'))
          .finally(() => setLoading(false));
    }, []);

    const handleSelectPlan = (planName: string) => {
        if (planName === 'FREE') {
            setSelectedPlan('FREE');
            setShowPaymentForm(false);
        } else {
            setSelectedPlan(planName);
            setShowPaymentForm(true);
        }
        setError('');
        setSuccess('');
    };

    const handleSubscribe = () => {
        if (!selectedPlan) return;
        setSubmitting(true);
        setError('');
        const payload: Record<string, unknown> = { planName: selectedPlan, billingCycle };
        if (selectedPlan !== 'FREE') {
            const brand = detectCardBrand(cardNumber);
            if (!cardNumber.trim() || !cardholderName.trim() || !expiryMonth || !expiryYear || !cvv.trim()) {
                setError('Please fill in all card details including the security code.');
                setSubmitting(false);
                return;
            }
            if (!brand) {
                setError('Only Visa and Mastercard are accepted.');
                setSubmitting(false);
                return;
            }
            if (cvv.length < 3) {
                setError('Please enter a valid security code (CVV).');
                setSubmitting(false);
                return;
            }
            payload.cardholderName = cardholderName;
            payload.cardNumber = cardNumber.replace(/\s/g, '');
            payload.expiryMonth = parseInt(expiryMonth);
            payload.expiryYear = parseInt(expiryYear);
            payload.billingZip = billingZip;
        }
        axios.post(`${API_GATEWAY}/api/v1/subscription/subscribe`, payload, { headers: authHeader() })
            .then(res => {
                setStatus(res.data);
                setShowPaymentForm(false);
                setSelectedPlan(null);
                setCardNumber('');
                setCardholderName('');
                setExpiryMonth('');
                setExpiryYear('');
                setBillingZip('');
                setCvv('');
                setSuccess('Subscription updated successfully!');
            })
            .catch(err => setError(err.response?.data?.message ?? 'Subscription failed. Please try again.'))
            .finally(() => setSubmitting(false));
    };

    const handleCancel = () => {
        if (!window.confirm('Are you sure you want to cancel your subscription? You will revert to the Free plan at the end of your billing period.')) return;
        axios.post(`${API_GATEWAY}/api/v1/subscription/cancel`, {}, { headers: authHeader() })
            .then(() => {
                setSuccess('Subscription cancelled. You will revert to Free at the end of the billing period.');
                return axios.get(`${API_GATEWAY}/api/v1/subscription/status`, { headers: authHeader() });
            })
            .then(res => setStatus(res.data))
            .catch(() => setError('Failed to cancel subscription.'));
    };

    const detectCardBrand = (val: string): 'Visa' | 'Mastercard' | null => {
        const digits = val.replace(/\D/g, '');
        if (digits.startsWith('4')) return 'Visa';
        if (/^5[1-5]/.test(digits) || /^2(2[2-9][1-9]|[3-6]\d{2}|7[01]\d|720)/.test(digits)) return 'Mastercard';
        return null;
    };

    const cardBrand = detectCardBrand(cardNumber);

    const formatCardNumber = (val: string) => {
        const digits = val.replace(/\D/g, '').slice(0, 16);
        return digits.replace(/(.{4})/g, '$1 ').trim();
    };

    const currentPlan = status?.planName ?? 'FREE';

    return (
        <div className="portfolio-page">
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/portfolio" className="logo">SpringHi.ai</Link>
                    {username() && <span className="nav-welcome">Welcome back, {username()}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/portfolio" className="btn-logout">Portfolios</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    <Link to="/account" className="btn-logout">Account</Link>
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <h1 className="portfolio-heading">Subscription</h1>
                <p className="portfolio-sub">Choose the plan that fits your needs.</p>

                {error && (
                    <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '1rem' }}>{error}</div>
                )}
                {success && (
                    <div style={{ background: '#d1fae5', color: '#065f46', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '1rem' }}>{success}</div>
                )}

                {loading ? (
                    <div className="portfolio-loading">Loading…</div>
                ) : (
                    <>
                        {status && (
                            <div style={{ background: 'var(--bg-card)', borderRadius: 10, border: '1px solid var(--border)', padding: '1.25rem', marginBottom: '2rem' }}>
                                <h2 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '0.75rem' }}>Current Plan</h2>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.5rem', fontSize: '0.9rem', color: 'var(--text-primary)', marginBottom: '1rem' }}>
                                    <span><strong>Plan:</strong> {status.displayName}</span>
                                    <span><strong>Status:</strong> <span style={{ color: status.status === 'ACTIVE' ? '#22c55e' : '#f59e0b' }}>{status.status}</span></span>
                                    {status.billingCycle && <span><strong>Billing:</strong> {status.billingCycle}</span>}
                                    {status.paymentMethod && (
                                        <span><strong>Card:</strong> {status.paymentMethod.cardBrand} ····{status.paymentMethod.cardLastFour} ({status.paymentMethod.expiryMonth}/{status.paymentMethod.expiryYear})</span>
                                    )}
                                </div>

                                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '0.75rem', marginBottom: usageStats ? '1rem' : 0 }}>
                                    <div style={{ background: 'var(--bg-input, #1e2035)', borderRadius: 8, padding: '0.75rem 1rem', border: '1px solid var(--border)' }}>
                                        <div style={{ fontSize: '0.78rem', color: 'var(--text-gray)', marginBottom: 4 }}>Portfolios</div>
                                        <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                                            {usageStats?.portfolioCount ?? '—'} <span style={{ fontSize: '0.85rem', fontWeight: 400, color: 'var(--text-gray)' }}>/ {status.maxPortfolios}</span>
                                        </div>
                                    </div>
                                    <div style={{ background: 'var(--bg-input, #1e2035)', borderRadius: 8, padding: '0.75rem 1rem', border: '1px solid var(--border)' }}>
                                        <div style={{ fontSize: '0.78rem', color: 'var(--text-gray)', marginBottom: 4 }}>AI Optimizations this month</div>
                                        <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
                                            {usageStats?.optimizationsThisMonth ?? '—'} <span style={{ fontSize: '0.85rem', fontWeight: 400, color: 'var(--text-gray)' }}>/ {status.maxOptimizationsPerMonth}</span>
                                        </div>
                                        {usageStats && (
                                            <div style={{ fontSize: '0.78rem', color: '#22c55e', marginTop: 2 }}>
                                                {Math.max(0, status.maxOptimizationsPerMonth - usageStats.optimizationsThisMonth)} remaining
                                            </div>
                                        )}
                                    </div>
                                    {currentPlan !== 'FREE' && status.nextBillingDate && (
                                        <div style={{ background: 'var(--bg-input, #1e2035)', borderRadius: 8, padding: '0.75rem 1rem', border: '1px solid var(--border)' }}>
                                            <div style={{ fontSize: '0.78rem', color: 'var(--text-gray)', marginBottom: 4 }}>
                                                {status.status === 'CANCELLED' ? 'Plan ends' : 'Next billing date'}
                                            </div>
                                            <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                                                {new Date(status.nextBillingDate).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })}
                                            </div>
                                        </div>
                                    )}
                                </div>

                                {currentPlan !== 'FREE' && status.status === 'ACTIVE' && (
                                    <button
                                        onClick={handleCancel}
                                        style={{ marginTop: '0.5rem', background: 'transparent', color: '#ef4444', border: '1px solid #ef4444', borderRadius: 6, padding: '0.35rem 0.85rem', fontSize: '0.85rem', cursor: 'pointer' }}
                                    >
                                        Cancel Subscription
                                    </button>
                                )}
                            </div>
                        )}

                        <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', alignItems: 'center' }}>
                            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-primary)' }}>Billing:</span>
                            {(['MONTHLY', 'ANNUAL'] as const).map(c => (
                                <button
                                    key={c}
                                    onClick={() => setBillingCycle(c)}
                                    style={{
                                        padding: '0.35rem 0.9rem',
                                        borderRadius: 6,
                                        border: '1px solid var(--border)',
                                        background: billingCycle === c ? '#6c47ff' : 'var(--bg-card)',
                                        color: billingCycle === c ? '#fff' : 'var(--text-primary)',
                                        fontWeight: billingCycle === c ? 700 : 400,
                                        cursor: 'pointer',
                                        fontSize: '0.88rem',
                                    }}
                                >
                                    {c === 'MONTHLY' ? 'Monthly' : 'Annual (save ~17%)'}
                                </button>
                            ))}
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
                            {plans.map(plan => {
                                const isCurrent = plan.planName === currentPlan;
                                const isSelected = plan.planName === selectedPlan;
                                const price = billingCycle === 'ANNUAL' ? plan.annualPrice : plan.monthlyPrice;
                                return (
                                    <div
                                        key={plan.planName}
                                        style={{
                                            background: isSelected ? 'rgba(108,71,255,0.1)' : 'var(--bg-card)',
                                            border: `2px solid ${isSelected ? '#6c47ff' : isCurrent ? '#22c55e' : 'var(--border)'}`,
                                            borderRadius: 12,
                                            padding: '1.5rem',
                                            cursor: 'pointer',
                                            transition: 'border-color 0.15s',
                                        }}
                                        onClick={() => handleSelectPlan(plan.planName)}
                                    >
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                                            <h3 style={{ fontWeight: 700, fontSize: '1.05rem', margin: 0, color: 'var(--text-primary)' }}>{plan.displayName}</h3>
                                            {isCurrent && (
                                                <span style={{ background: '#22c55e', color: '#fff', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.75rem', fontWeight: 700 }}>Current</span>
                                            )}
                                        </div>
                                        <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', marginBottom: '0.25rem' }}>
                                            {price === 0 ? 'Free' : `$${price}`}
                                            {price > 0 && <span style={{ fontSize: '0.85rem', fontWeight: 400, color: 'var(--text-gray)' }}>/{billingCycle === 'ANNUAL' ? 'yr' : 'mo'}</span>}
                                        </div>
                                        {billingCycle === 'ANNUAL' && plan.monthlyPrice > 0 && (
                                            <div style={{ fontSize: '0.8rem', color: '#6c47ff', marginBottom: '0.5rem' }}>
                                                (${(plan.annualPrice / 12).toFixed(2)}/mo billed annually)
                                            </div>
                                        )}
                                        <ul style={{ listStyle: 'none', padding: 0, margin: '0.75rem 0 1rem', fontSize: '0.88rem', color: 'var(--text-gray)' }}>
                                            <li style={{ marginBottom: 4 }}>✓ {plan.maxPortfolios} portfolios</li>
                                            <li style={{ marginBottom: 4 }}>✓ {plan.maxOptimizationsPerMonth} AI optimizations/month</li>
                                        </ul>
                                        <button
                                            style={{
                                                width: '100%',
                                                padding: '0.5rem',
                                                borderRadius: 7,
                                                border: 'none',
                                                background: isCurrent ? '#374151' : '#6c47ff',
                                                color: '#fff',
                                                fontWeight: 700,
                                                fontSize: '0.9rem',
                                                cursor: isCurrent ? 'default' : 'pointer',
                                                opacity: isCurrent ? 0.7 : 1,
                                            }}
                                            onClick={e => { e.stopPropagation(); handleSelectPlan(plan.planName); }}
                                            disabled={isCurrent}
                                        >
                                            {isCurrent ? 'Current Plan' : 'Select'}
                                        </button>
                                    </div>
                                );
                            })}
                        </div>



                        {selectedPlan === 'FREE' && selectedPlan !== currentPlan && (
                            <div style={{ background: 'var(--bg-card)', borderRadius: 10, border: '1px solid var(--border)', padding: '1.25rem', maxWidth: 480 }}>
                                <p style={{ color: 'var(--text-primary)', marginBottom: '1rem' }}>
                                    Downgrading to the Free plan will take effect at the end of your current billing period.
                                </p>
                                <button
                                    onClick={handleCancel}
                                    style={{ background: '#374151', color: '#fff', border: 'none', borderRadius: 7, padding: '0.5rem 1.25rem', fontWeight: 700, cursor: 'pointer' }}
                                >
                                    Confirm Downgrade to Free
                                </button>
                            </div>
                        )}
                    </>
                )}
            </main>

            {showPaymentForm && selectedPlan && selectedPlan !== 'FREE' && (
                <div
                    style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}
                    onClick={() => { setShowPaymentForm(false); setSelectedPlan(null); setError(''); }}
                >
                    <div
                        style={{ background: 'var(--bg-card)', borderRadius: 12, border: '1px solid var(--border)', padding: '1.75rem', width: '100%', maxWidth: 480, margin: '1rem', boxShadow: '0 20px 60px rgba(0,0,0,0.5)' }}
                        onClick={e => e.stopPropagation()}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem' }}>
                            <div>
                                <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>Payment Details</h2>
                                <div style={{ fontSize: '0.82rem', color: 'var(--text-gray)', marginTop: 3 }}>
                                    {plans.find(p => p.planName === selectedPlan)?.displayName} — ${billingCycle === 'ANNUAL' ? plans.find(p => p.planName === selectedPlan)?.annualPrice : plans.find(p => p.planName === selectedPlan)?.monthlyPrice}/{billingCycle === 'ANNUAL' ? 'yr' : 'mo'}
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
                                <span style={{ background: '#1a1f71', color: '#fff', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.75rem', fontWeight: 700, letterSpacing: 1 }}>VISA</span>
                                <span style={{ background: '#eb001b', color: '#fff', borderRadius: 4, padding: '0.1rem 0.5rem', fontSize: '0.75rem', fontWeight: 700 }}>MC</span>
                                <button
                                    onClick={() => { setShowPaymentForm(false); setSelectedPlan(null); setError(''); }}
                                    style={{ background: 'transparent', border: 'none', color: 'var(--text-gray)', fontSize: '1.25rem', cursor: 'pointer', lineHeight: 1, marginLeft: '0.25rem' }}
                                    aria-label="Close"
                                >×</button>
                            </div>
                        </div>

                        <div style={{ marginBottom: '1rem' }}>
                            <label className="form-label">Cardholder Name</label>
                            <input
                                type="text"
                                className="profile-input"
                                value={cardholderName}
                                onChange={e => setCardholderName(e.target.value)}
                                placeholder="Name on card"
                                autoComplete="cc-name"
                            />
                        </div>

                        <div style={{ marginBottom: '1rem' }}>
                            <label className="form-label">
                                Card Number
                                {cardBrand && (
                                    <span style={{ marginLeft: '0.5rem', background: cardBrand === 'Visa' ? '#1a1f71' : '#eb001b', color: '#fff', borderRadius: 4, padding: '0.05rem 0.45rem', fontSize: '0.72rem', fontWeight: 700, letterSpacing: 0.5, verticalAlign: 'middle' }}>
                                        {cardBrand === 'Visa' ? 'VISA' : 'MC'}
                                    </span>
                                )}
                                {cardNumber.replace(/\s/g, '').length >= 4 && !cardBrand && (
                                    <span style={{ marginLeft: '0.5rem', color: '#ef4444', fontSize: '0.78rem' }}>Only Visa &amp; Mastercard accepted</span>
                                )}
                            </label>
                            <input
                                type="text"
                                className="profile-input"
                                value={cardNumber}
                                onChange={e => setCardNumber(formatCardNumber(e.target.value))}
                                placeholder="1234 5678 9012 3456"
                                maxLength={19}
                                autoComplete="cc-number"
                                inputMode="numeric"
                            />
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: '0.75rem', marginBottom: '1rem' }}>
                            <div>
                                <label className="form-label">Exp Month</label>
                                <input
                                    type="number"
                                    className="profile-input"
                                    value={expiryMonth}
                                    onChange={e => setExpiryMonth(e.target.value)}
                                    placeholder="MM"
                                    min={1}
                                    max={12}
                                    autoComplete="cc-exp-month"
                                />
                            </div>
                            <div>
                                <label className="form-label">Exp Year</label>
                                <input
                                    type="number"
                                    className="profile-input"
                                    value={expiryYear}
                                    onChange={e => setExpiryYear(e.target.value)}
                                    placeholder="YYYY"
                                    min={2024}
                                    autoComplete="cc-exp-year"
                                />
                            </div>
                            <div>
                                <label className="form-label">CVV</label>
                                <input
                                    type="text"
                                    className="profile-input"
                                    value={cvv}
                                    onChange={e => setCvv(e.target.value.replace(/\D/g, '').slice(0, 4))}
                                    placeholder="123"
                                    maxLength={4}
                                    autoComplete="cc-csc"
                                    inputMode="numeric"
                                />
                            </div>
                            <div>
                                <label className="form-label">ZIP</label>
                                <input
                                    type="text"
                                    className="profile-input"
                                    value={billingZip}
                                    onChange={e => setBillingZip(e.target.value)}
                                    placeholder="ZIP"
                                    maxLength={10}
                                    autoComplete="postal-code"
                                />
                            </div>
                        </div>

                        <p style={{ fontSize: '0.78rem', color: 'var(--text-gray)', marginBottom: '1rem' }}>
                            Your card information is stored securely. No real charges will be processed until a payment provider is integrated.
                        </p>

                        {error && (
                            <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 7, padding: '0.6rem 0.85rem', marginBottom: '0.75rem', fontSize: '0.88rem' }}>{error}</div>
                        )}

                        <div style={{ display: 'flex', gap: '0.75rem' }}>
                            <button
                                onClick={handleSubscribe}
                                disabled={submitting}
                                style={{ background: '#6c47ff', color: '#fff', border: 'none', borderRadius: 7, padding: '0.5rem 1.25rem', fontWeight: 700, fontSize: '0.9rem', cursor: submitting ? 'default' : 'pointer', opacity: submitting ? 0.7 : 1 }}
                            >
                                {submitting ? 'Processing…' : `Subscribe — $${billingCycle === 'ANNUAL' ? plans.find(p => p.planName === selectedPlan)?.annualPrice : plans.find(p => p.planName === selectedPlan)?.monthlyPrice}/${billingCycle === 'ANNUAL' ? 'yr' : 'mo'}`}
                            </button>
                            <button
                                onClick={() => { setShowPaymentForm(false); setSelectedPlan(null); setError(''); }}
                                style={{ background: 'transparent', color: 'var(--text-gray)', border: '1px solid var(--border)', borderRadius: 7, padding: '0.5rem 1rem', cursor: 'pointer' }}
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Subscription;
