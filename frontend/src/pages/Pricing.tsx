import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { Check } from 'lucide-react';
import API_GATEWAY from '../api/apiBase';
import { isLoggedIn } from '../utils/auth';

interface Plan {
    planName: string;
    displayName: string;
    monthlyPrice: number;
    annualPrice: number;
    maxPortfolios: number;
    maxOptimizationsPerMonth: number;
    description: string;
}

const STATIC_FEATURE_ROWS: { label: string; free: string; basic: string; premium: string }[] = [
    { label: 'Scheduled auto-optimization',   free: '—',             basic: '✓',             premium: '✓' },
    { label: 'AI models',                     free: 'Claude · ChatGPT · Gemini', basic: 'Claude · ChatGPT · Gemini', premium: 'Claude · ChatGPT · Gemini' },
    { label: 'Rebalancing frequencies',       free: '—',             basic: 'Daily to Yearly', premium: 'Daily to Yearly' },
    { label: 'Leaderboard visibility',        free: '✓',             basic: '✓',             premium: '✓' },
    { label: 'Full AI run transparency',      free: '✓',             basic: '✓',             premium: '✓' },
    { label: 'Real market data',              free: '✓',             basic: '✓',             premium: '✓' },
    { label: 'TWR performance tracking',      free: '✓',             basic: '✓',             premium: '✓' },
    { label: 'S&P 500 benchmark comparison',  free: '✓',             basic: '✓',             premium: '✓' },
    { label: 'Tax optimization mode',         free: '✓',             basic: '✓',             premium: '✓' },
];

const ACCENT: Record<string, string> = {
    FREE: '#3a3a3c',
    BASIC: '#6366f1',
    PREMIUM: '#f59e0b',
};

const Pricing: React.FC = () => {
    const navigate = useNavigate();
    const [plans, setPlans] = useState<Plan[]>([]);
    const [billing, setBilling] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        axios.get(`${API_GATEWAY}/api/v1/subscription/plans`)
            .then(r => setPlans(r.data))
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    const price = (plan: Plan) => billing === 'ANNUAL' ? plan.annualPrice : plan.monthlyPrice;

    const byName = (name: string) => plans.find(p => p.planName === name);

    const dynamicFeatureRows = (freePlan: Plan | undefined, basicPlan: Plan | undefined, premiumPlan: Plan | undefined) => [
        {
            label: 'Portfolios',
            free: freePlan ? String(freePlan.maxPortfolios) : '—',
            basic: basicPlan ? String(basicPlan.maxPortfolios) : '—',
            premium: premiumPlan ? String(premiumPlan.maxPortfolios) : '—',
        },
        {
            label: 'AI Optimizations',
            free: freePlan ? `${freePlan.maxOptimizationsPerMonth} (lifetime)` : '—',
            basic: basicPlan ? `${basicPlan.maxOptimizationsPerMonth} / month` : '—',
            premium: premiumPlan ? `${premiumPlan.maxOptimizationsPerMonth} / month` : '—',
        },
        ...STATIC_FEATURE_ROWS,
    ];

    const planHighlights = (plan: Plan): string[] => {
        if (plan.planName === 'FREE') return [
            'Get started with no commitment',
            'Explore all 3 AI models',
            `${plan.maxPortfolios} portfolios · ${plan.maxOptimizationsPerMonth} lifetime optimizations`,
        ];
        if (plan.planName === 'BASIC') return [
            'Everything in Free',
            `${plan.maxOptimizationsPerMonth} AI optimizations per month`,
            'Scheduled auto-rebalancing',
        ];
        return [
            'Everything in Basic',
            `${plan.maxOptimizationsPerMonth} AI optimizations per month`,
            `Up to ${plan.maxPortfolios} portfolios`,
        ];
    };

    const handleCta = (planName: string) => {
        if (!isLoggedIn()) {
            navigate('/signup');
            return;
        }
        navigate('/subscription');
    };

    return (
        <div style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>

            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Link to="/" className="nav-link">Home</Link>
                    <Link to="/about" className="nav-link">About</Link>
                    {isLoggedIn() ? (
                        <Link to="/portfolio" className="btn-primary">My Portfolio</Link>
                    ) : (
                        <>
                            <Link to="/login" className="nav-link">Login</Link>
                            <Link to="/signup" className="btn-primary">Get Started Free</Link>
                        </>
                    )}
                </nav>
            </header>

            <main style={{ padding: '5rem 8% 6rem' }}>

                <div style={{ textAlign: 'center', marginBottom: '3rem' }}>
                    <h1 style={{
                        fontSize: 'clamp(2rem, 5vw, 3.2rem)',
                        fontWeight: 900,
                        lineHeight: 1.15,
                        background: 'linear-gradient(100deg, #fff 0%, #60a5fa 60%, #a78bfa 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        marginBottom: '1rem',
                    }}>
                        Simple, transparent pricing
                    </h1>
                    <p style={{ color: '#a0a0a0', fontSize: '1.05rem', maxWidth: 540, margin: '0 auto 2rem', lineHeight: 1.7 }}>
                        Start free. Upgrade when you're ready to run more optimizations or automate your rebalancing.
                    </p>

                    <div style={{
                        display: 'inline-flex',
                        background: '#161618',
                        border: '1px solid #2a2a2c',
                        borderRadius: 10,
                        padding: '0.25rem',
                        gap: '0.25rem',
                    }}>
                        {(['MONTHLY', 'ANNUAL'] as const).map(cycle => (
                            <button
                                key={cycle}
                                onClick={() => setBilling(cycle)}
                                style={{
                                    padding: '0.45rem 1.25rem',
                                    borderRadius: 7,
                                    border: 'none',
                                    cursor: 'pointer',
                                    fontSize: '0.9rem',
                                    fontWeight: 600,
                                    background: billing === cycle ? '#fff' : 'transparent',
                                    color: billing === cycle ? '#0a0a0b' : '#a0a0a0',
                                    transition: 'all 0.15s',
                                }}
                            >
                                {cycle === 'MONTHLY' ? 'Monthly' : 'Annual'}
                                {cycle === 'ANNUAL' && (
                                    <span style={{ marginLeft: '0.4rem', background: '#22c55e', color: '#fff', borderRadius: 4, padding: '0.05rem 0.35rem', fontSize: '0.7rem', fontWeight: 700 }}>
                                        Save
                                    </span>
                                )}
                            </button>
                        ))}
                    </div>
                </div>

                {loading ? (
                    <div style={{ textAlign: 'center', color: '#a0a0a0', padding: '3rem' }}>Loading plans…</div>
                ) : (
                    <>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(270px, 1fr))', gap: '1.5rem', maxWidth: 960, margin: '0 auto 4rem' }}>
                            {plans.map(plan => {
                                const p = price(plan);
                                const accent = ACCENT[plan.planName] ?? '#6366f1';
                                const highlights = planHighlights(plan);
                                const isPopular = plan.planName === 'BASIC';
                                return (
                                    <div
                                        key={plan.planName}
                                        style={{
                                            position: 'relative',
                                            background: '#161618',
                                            border: `2px solid ${isPopular ? accent : '#2a2a2c'}`,
                                            borderRadius: 16,
                                            padding: '2rem',
                                            display: 'flex',
                                            flexDirection: 'column',
                                        }}
                                    >
                                        {isPopular && (
                                            <div style={{
                                                position: 'absolute', top: -13, left: '50%', transform: 'translateX(-50%)',
                                                background: accent, color: '#fff', borderRadius: 20,
                                                padding: '0.2rem 0.9rem', fontSize: '0.75rem', fontWeight: 700, whiteSpace: 'nowrap',
                                            }}>
                                                Most Popular
                                            </div>
                                        )}

                                        <h2 style={{ fontSize: '1.2rem', fontWeight: 800, marginBottom: '0.5rem', color: '#fff' }}>{plan.displayName}</h2>
                                        <p style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.6, marginBottom: '1.25rem', minHeight: 40 }}>{plan.description}</p>

                                        <div style={{ marginBottom: '1.5rem' }}>
                                            <span style={{ fontSize: '2.5rem', fontWeight: 900, color: '#fff' }}>
                                                {p === 0 ? 'Free' : `$${p}`}
                                            </span>
                                            {p > 0 && (
                                                <span style={{ color: '#a0a0a0', fontSize: '0.9rem', marginLeft: '0.3rem' }}>
                                                    /{billing === 'ANNUAL' ? 'yr' : 'mo'}
                                                </span>
                                            )}
                                            {billing === 'ANNUAL' && plan.monthlyPrice > 0 && (
                                                <div style={{ fontSize: '0.8rem', color: '#22c55e', marginTop: '0.2rem' }}>
                                                    (${(plan.annualPrice / 12).toFixed(2)}/mo billed annually)
                                                </div>
                                            )}
                                        </div>

                                        <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                                            {highlights.map(h => (
                                                <li key={h} style={{ display: 'flex', alignItems: 'flex-start', gap: '0.5rem', fontSize: '0.875rem', color: '#d1d5db' }}>
                                                    <Check size={15} color={accent === '#3a3a3c' ? '#a0a0a0' : accent} style={{ marginTop: 2, flexShrink: 0 }} />
                                                    {h}
                                                </li>
                                            ))}
                                        </ul>

                                        <button
                                            onClick={() => handleCta(plan.planName)}
                                            style={{
                                                marginTop: 'auto',
                                                padding: '0.75rem',
                                                borderRadius: 10,
                                                border: 'none',
                                                cursor: 'pointer',
                                                fontWeight: 700,
                                                fontSize: '0.95rem',
                                                background: plan.planName === 'FREE' ? 'transparent' : accent,
                                                color: plan.planName === 'FREE' ? '#a0a0a0' : '#fff',
                                                border: plan.planName === 'FREE' ? '1px solid #3a3a3c' : 'none',
                                            } as React.CSSProperties}
                                        >
                                            {plan.planName === 'FREE' ? 'Get Started Free' : isLoggedIn() ? `Upgrade to ${plan.displayName}` : `Start with ${plan.displayName}`}
                                        </button>
                                    </div>
                                );
                            })}
                        </div>

                        <section style={{ maxWidth: 960, margin: '0 auto 4rem' }}>
                            <h2 style={{ textAlign: 'center', fontSize: '1.4rem', fontWeight: 800, marginBottom: '1.5rem' }}>Full feature comparison</h2>
                            <div style={{ background: '#161618', border: '1px solid #2a2a2c', borderRadius: 14, overflow: 'hidden' }}>
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
                                    <thead>
                                        <tr style={{ background: '#0e0e10' }}>
                                            <th style={{ padding: '0.9rem 1.25rem', textAlign: 'left', color: '#a0a0a0', fontWeight: 600, width: '40%' }}>Feature</th>
                                            <th style={{ padding: '0.9rem 1rem', textAlign: 'center', color: '#fff', fontWeight: 700 }}>Free</th>
                                            <th style={{ padding: '0.9rem 1rem', textAlign: 'center', color: '#a78bfa', fontWeight: 700 }}>Basic</th>
                                            <th style={{ padding: '0.9rem 1rem', textAlign: 'center', color: '#f59e0b', fontWeight: 700 }}>Premium</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {dynamicFeatureRows(byName('FREE'), byName('BASIC'), byName('PREMIUM')).map((row, i) => (
                                            <tr key={row.label} style={{ borderTop: '1px solid #2a2a2c', background: i % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.015)' }}>
                                                <td style={{ padding: '0.75rem 1.25rem', color: '#d1d5db' }}>{row.label}</td>
                                                <td style={{ padding: '0.75rem 1rem', textAlign: 'center', color: row.free === '—' ? '#4a4a4c' : '#a0a0a0' }}>{row.free}</td>
                                                <td style={{ padding: '0.75rem 1rem', textAlign: 'center', color: row.basic === '—' ? '#4a4a4c' : '#c4b5fd' }}>{row.basic}</td>
                                                <td style={{ padding: '0.75rem 1rem', textAlign: 'center', color: row.premium === '—' ? '#4a4a4c' : '#fcd34d' }}>{row.premium}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </>
                )}

                <section style={{ textAlign: 'center', background: '#161618', border: '1px solid #2a2a2c', borderRadius: 16, padding: '3rem', maxWidth: 640, margin: '0 auto' }}>
                    <h2 style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '0.75rem' }}>Questions?</h2>
                    <p style={{ color: '#a0a0a0', lineHeight: 1.7, marginBottom: '1.5rem' }}>
                        All plans use paper trading — no real money is ever at risk. Upgrade or cancel anytime.
                        Paid subscriptions can use Visa or Mastercard.
                    </p>
                    {!isLoggedIn() && (
                        <Link to="/signup" style={{
                            background: '#6366f1', color: '#fff', padding: '0.75rem 2rem',
                            borderRadius: 10, textDecoration: 'none', fontWeight: 700, fontSize: '0.95rem',
                        }}>
                            Create a free account
                        </Link>
                    )}
                </section>
            </main>

            <footer style={{ textAlign: 'center', padding: '2.5rem', color: '#4a4a4c', borderTop: '1px solid #1e1e20', fontSize: '0.85rem' }}>
                &copy; {new Date().getFullYear()} SpringHi.ai — Paper trading for education and competition purposes only. Not investment advice.
            </footer>
        </div>
    );
};

export default Pricing;
