import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, Trophy, CalendarClock, BookOpen, GitCompare, ShieldAlert, ChevronLeft, ChevronRight } from 'lucide-react';

const isLoggedIn = () => !!localStorage.getItem('token');

const SLIDES = [
    {
        label: 'Portfolio Dashboard',
        caption: 'Track every holding in real time — live prices, unrealized gains/losses, cost basis, and your full P&L at a glance.',
        src: 'src/images/portfolio.jpg',
    },
    {
        label: 'Leaderboard',
        caption: 'See how every portfolio ranks against peers and the S&P 500. Monthly and all-time — with TWR and benchmark margin.',
        src: 'src/images/leaderboard.jpg',
    },
    {
        label: 'Trade History',
        caption: 'Every buy, sell, deposit, and withdrawal logged with timestamp, price, and quantity. Full audit trail, always.',
        src: 'src/images/transactions.jpg',
    },
    {
        label: 'Executed AI Recommendations',
        caption: 'The AI generates a prioritized buy/sell plan tailored to your profile. Review each recommendation and execute with one click.',
        src: 'src/images/optimization.jpg',
    },
];

const ScreenshotCarousel: React.FC = () => {
    const [current, setCurrent] = useState(0);
    const [paused, setPaused] = useState(false);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const advance = (dir: 1 | -1) => {
        setCurrent(c => (c + dir + SLIDES.length) % SLIDES.length);
    };

    useEffect(() => {
        if (paused) return;
        timerRef.current = setInterval(() => setCurrent(c => (c + 1) % SLIDES.length), 4500);
        return () => { if (timerRef.current) clearInterval(timerRef.current); };
    }, [paused]);

    const slide = SLIDES[current];

    return (
        <div
            onMouseEnter={() => setPaused(true)}
            onMouseLeave={() => setPaused(false)}
            style={{ position: 'relative', maxWidth: 900, margin: '0 auto' }}
        >
            <div style={{
                background: '#161618',
                border: '1px solid #2a2a2c',
                borderRadius: 16,
                overflow: 'hidden',
            }}>
                <div style={{
                    position: 'relative',
                    width: '100%',
                    aspectRatio: '16/9',
                    background: '#0e0e10',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '1rem',
                }}>
                    {slide.src ? (
                        <img src={slide.src} alt={slide.label} style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                    ) : (
                        <>
                            <div style={{
                                width: 72, height: 72,
                                background: 'rgba(99,102,241,0.15)',
                                border: '2px solid rgba(99,102,241,0.35)',
                                borderRadius: '50%',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                fontSize: '2rem',
                            }}>📸</div>
                            <span style={{ color: '#4a4a4c', fontSize: '0.9rem', fontWeight: 600, letterSpacing: '0.04em', textTransform: 'uppercase' }}>
                                Screenshot coming soon
                            </span>
                        </>
                    )}

                    <button
                        onClick={() => advance(-1)}
                        aria-label="Previous"
                        style={{
                            position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)',
                            background: 'rgba(0,0,0,0.6)', border: '1px solid #3a3a3c', borderRadius: 8,
                            color: '#fff', cursor: 'pointer', padding: '0.4rem', display: 'flex', alignItems: 'center',
                        }}
                    >
                        <ChevronLeft size={22} />
                    </button>
                    <button
                        onClick={() => advance(1)}
                        aria-label="Next"
                        style={{
                            position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
                            background: 'rgba(0,0,0,0.6)', border: '1px solid #3a3a3c', borderRadius: 8,
                            color: '#fff', cursor: 'pointer', padding: '0.4rem', display: 'flex', alignItems: 'center',
                        }}
                    >
                        <ChevronRight size={22} />
                    </button>
                </div>

                <div style={{ padding: '1.25rem 1.5rem' }}>
                    <div style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '0.35rem', color: '#fff' }}>{slide.label}</div>
                    <div style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.6 }}>{slide.caption}</div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', paddingBottom: '1.25rem' }}>
                    {SLIDES.map((_, i) => (
                        <button
                            key={i}
                            onClick={() => setCurrent(i)}
                            aria-label={`Go to slide ${i + 1}`}
                            style={{
                                width: i === current ? 20 : 8,
                                height: 8,
                                borderRadius: 4,
                                border: 'none',
                                background: i === current ? '#6366f1' : '#3a3a3c',
                                cursor: 'pointer',
                                padding: 0,
                                transition: 'width 0.25s ease, background 0.25s ease',
                            }}
                        />
                    ))}
                </div>
            </div>
        </div>
    );
};

const Home: React.FC = () => {
    return (
        <div className="home-container" style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>

            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Link to="/getting-started" className="nav-link">Getting Started</Link>
                    <Link to="/about" className="nav-link">About</Link>
                    <Link to="/pricing" className="nav-link">Pricing</Link>
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

            <main style={{ textAlign: 'center', padding: '7rem 10% 5rem' }}>
                <div style={{
                    display: 'inline-block',
                    background: 'rgba(0,102,255,0.12)',
                    border: '1px solid rgba(0,102,255,0.35)',
                    borderRadius: 20,
                    padding: '0.35rem 1rem',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    color: '#60a5fa',
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    marginBottom: '1.5rem',
                }}>
                    Paper Trading · No Real Money at Risk
                </div>
                <h1 style={{
                    fontSize: 'clamp(2.5rem, 6vw, 4.5rem)',
                    fontWeight: 900,
                    lineHeight: 1.1,
                    marginBottom: '1.5rem',
                    background: 'linear-gradient(100deg, #fff 0%, #60a5fa 60%, #a78bfa 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                }}>
                    Compete. Learn. Experiment.<br />Let AI Run Your Portfolio.
                </h1>
                <p style={{ fontSize: '1.2rem', color: '#a0a0a0', maxWidth: 700, margin: '0 auto 2.5rem', lineHeight: 1.7 }}>
                    The only platform where you can copy the #1 portfolio's exact AI prompt, swap in a different
                    model, change the rebalancing frequency, and see if <em>you</em> can beat it — all without
                    putting a single dollar at risk.
                </p>
                <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
                    {isLoggedIn() ? (
                        <Link to="/portfolio" style={{
                            background: '#0066ff',
                            color: '#fff',
                            padding: '1rem 2.5rem',
                            borderRadius: 12,
                            textDecoration: 'none',
                            fontSize: '1.05rem',
                            fontWeight: 700,
                        }}>
                            My Portfolio
                        </Link>
                    ) : (
                        <Link to="/signup" style={{
                            background: '#0066ff',
                            color: '#fff',
                            padding: '1rem 2.5rem',
                            borderRadius: 12,
                            textDecoration: 'none',
                            fontSize: '1.05rem',
                            fontWeight: 700,
                        }}>
                            Start for Free
                        </Link>
                    )}
                    <Link to={isLoggedIn() ? '/portfolio' : '/signup'} style={{
                        background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
                        color: '#fff',
                        padding: '1rem 2.5rem',
                        borderRadius: 12,
                        textDecoration: 'none',
                        fontSize: '1.05rem',
                        fontWeight: 700,
                    }}>
                        Try AI Optimization Now
                    </Link>
                    <Link to="/leaderboard" style={{
                        background: 'transparent',
                        color: '#fff',
                        padding: '1rem 2.5rem',
                        borderRadius: 12,
                        textDecoration: 'none',
                        fontSize: '1.05rem',
                        fontWeight: 600,
                        border: '1px solid #3a3a3c',
                    }}>
                        View Leaderboard
                    </Link>
                </div>
            </main>

            <section style={{ padding: '1rem 10% 5rem', textAlign: 'center' }}>
                <div style={{
                    display: 'inline-flex',
                    gap: '3rem',
                    background: '#161618',
                    border: '1px solid #2a2a2c',
                    borderRadius: 16,
                    padding: '1.5rem 3rem',
                    flexWrap: 'wrap',
                    justifyContent: 'center',
                }}>
                    {[
                        { label: 'Risk', value: 'Zero' },
                        { label: 'AI Models', value: '3' },
                        { label: 'Benchmark', value: 'S&P 500' },
                        { label: 'Transparency', value: 'Full' },
                    ].map(stat => (
                        <div key={stat.label} style={{ textAlign: 'center' }}>
                            <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#60a5fa' }}>{stat.value}</div>
                            <div style={{ fontSize: '0.8rem', color: '#a0a0a0', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{stat.label}</div>
                        </div>
                    ))}
                </div>
            </section>

            <section style={{ padding: '2rem 10% 6rem' }}>
                <h2 style={{ textAlign: 'center', fontSize: '2rem', fontWeight: 800, marginBottom: '0.75rem' }}>
                    Everything a hedge fund has. None of the risk.
                </h2>
                <p style={{ textAlign: 'center', color: '#a0a0a0', marginBottom: '3.5rem', fontSize: '1.05rem' }}>
                    Paper trading means you practice with real market data — with zero dollars on the line.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1.5rem' }}>
                    {[
                        {
                            icon: <BookOpen size={32} color="#a78bfa" />,
                            title: 'Learn from Every Top Strategy',
                            body: 'The #1 portfolio on the leaderboard is an open book. Read the exact AI prompt that generated it, every buy and sell recommendation, and the profile settings behind the decisions. Study, adapt, and apply what works.',
                        },
                        {
                            icon: <ShieldAlert size={32} color="#f87171" />,
                            title: 'Understand Risk Without Consequences',
                            body: 'Paper trading lets you set an aggressive risk profile, watch how the AI behaves under it, and measure the volatility — all with zero real-money exposure. Build an intuition for risk that would otherwise cost you tuition.',
                        },
                        {
                            icon: <CalendarClock size={32} color="#34d399" />,
                            title: 'Experiment with Rebalancing Frequencies',
                            body: 'Run one portfolio daily and another monthly. See which frequency outperforms over time. The scheduled auto-optimizer executes trades on market-open days so you get a genuine apples-to-apples comparison.',
                        },
                        {
                            icon: <GitCompare size={32} color="#60a5fa" />,
                            title: 'Compare How Different AIs Think',
                            body: 'Claude, ChatGPT, and Gemini each reason differently about the same portfolio profile. Give all three identical settings and watch them diverge — different sectors, different concentration, different conviction.',
                        },
                        {
                            icon: <Trophy size={32} color="#f59e0b" />,
                            title: 'Monthly Competitions',
                            body: 'Every portfolio competes in monthly leaderboards against peers and the S&P 500. See raw returns and your margin vs. the benchmark across 1W, 1M, 3M, 6M, and 1Y timeframes. Fresh competition every month.',
                        },
                        {
                            icon: <TrendingUp size={32} color="#22c55e" />,
                            title: 'Institutional-Grade Performance Tracking',
                            body: 'Time-Weighted Return (TWR) — the same methodology used by professional fund managers — measures your true performance independent of cash deposits. Always know if you\'re beating the market.',
                        },
                    ].map(card => (
                        <div key={card.title} style={{
                            background: '#161618',
                            border: '1px solid #2a2a2c',
                            borderRadius: 16,
                            padding: '2rem',
                        }}>
                            <div style={{ marginBottom: '1rem' }}>{card.icon}</div>
                            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '0.6rem' }}>{card.title}</h3>
                            <p style={{ color: '#a0a0a0', fontSize: '0.9rem', lineHeight: 1.65, margin: 0 }}>{card.body}</p>
                        </div>
                    ))}
                </div>
            </section>

            <section style={{ padding: '4rem 10%' }}>
                <h2 style={{ textAlign: 'center', fontSize: '1.9rem', fontWeight: 800, marginBottom: '0.75rem' }}>
                    See it in action
                </h2>
                <p style={{ textAlign: 'center', color: '#a0a0a0', fontSize: '1rem', marginBottom: '2.5rem' }}>
                    A look at what you get on day one.
                </p>
                <ScreenshotCarousel />
            </section>

            <section style={{ padding: '4rem 10%', background: '#0e0e10', borderTop: '1px solid #1e1e20', borderBottom: '1px solid #1e1e20' }}>
                <h2 style={{ textAlign: 'center', fontSize: '1.9rem', fontWeight: 800, marginBottom: '3rem' }}>
                    How it works
                </h2>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '2rem', maxWidth: 900, margin: '0 auto' }}>
                    {[
                        { step: '1', title: 'Create a portfolio', desc: 'Name it, set your risk profile, investment goals, and time horizon.' },
                        { step: '2', title: 'Run AI optimization', desc: 'Pick Claude, ChatGPT, or Gemini. The AI recommends a mix of buys and sells matched to your profile.' },
                        { step: '3', title: 'Execute & track', desc: 'Execute trades with one click. Watch your portfolio track real market prices in real time.' },
                        { step: '4', title: 'Compete & learn', desc: 'See how you rank vs. the S&P 500 and other investors — and study any competitor\'s exact strategy.' },
                    ].map(item => (
                        <div key={item.step} style={{ textAlign: 'center' }}>
                            <div style={{
                                width: 48, height: 48,
                                background: 'rgba(0,102,255,0.15)',
                                border: '2px solid rgba(0,102,255,0.4)',
                                borderRadius: '50%',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                fontSize: '1.1rem', fontWeight: 800, color: '#60a5fa',
                                margin: '0 auto 1rem',
                            }}>
                                {item.step}
                            </div>
                            <h4 style={{ fontWeight: 700, marginBottom: '0.5rem' }}>{item.title}</h4>
                            <p style={{ color: '#a0a0a0', fontSize: '0.88rem', lineHeight: 1.6, margin: 0 }}>{item.desc}</p>
                        </div>
                    ))}
                </div>
            </section>

            <section style={{ padding: '4rem 10%', textAlign: 'center' }}>
                <h2 style={{ fontSize: '1.9rem', fontWeight: 800, marginBottom: '0.75rem' }}>
                    What you'll actually learn
                </h2>
                <p style={{ color: '#a0a0a0', fontSize: '1rem', marginBottom: '2.5rem', maxWidth: 600, margin: '0 auto 2.5rem' }}>
                    Most investing platforms teach you nothing. SpringHi.ai is designed to teach you everything.
                </p>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.25rem', maxWidth: 1000, margin: '0 auto' }}>
                    {[
                        { q: 'What does the winning strategy actually look like?', a: 'Open any top portfolio and read the AI prompt, profile settings, and every trade — word for word.' },
                        { q: 'How does risk tolerance shape a portfolio?', a: 'Set aggressive vs. conservative profiles and watch the AI build completely different allocations from the same cash.' },
                        { q: 'Does daily rebalancing beat monthly?', a: 'Run two portfolios with the same profile but different schedules, then compare their TWR side by side.' },
                        { q: 'Does Claude pick different stocks than ChatGPT?', a: 'Give all three models the same profile and cash. The differences will surprise you.' },
                    ].map(item => (
                        <div key={item.q} style={{ background: '#161618', border: '1px solid #2a2a2c', borderRadius: 14, padding: '1.5rem', textAlign: 'left' }}>
                            <p style={{ fontWeight: 700, fontSize: '0.9rem', color: '#60a5fa', marginBottom: '0.6rem', margin: '0 0 0.6rem' }}>{item.q}</p>
                            <p style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.65, margin: 0 }}>{item.a}</p>
                        </div>
                    ))}
                </div>
            </section>

            <section style={{ textAlign: 'center', padding: '6rem 10%' }}>
                <h2 style={{ fontSize: '2.2rem', fontWeight: 800, marginBottom: '1rem' }}>
                    Ready to see if your AI picks beat the market?
                </h2>
                <p style={{ color: '#a0a0a0', fontSize: '1.05rem', marginBottom: '2.5rem' }}>
                    Free to start. No credit card required. No real money at risk.
                </p>
                <Link to="/signup" style={{
                    background: '#0066ff',
                    color: '#fff',
                    padding: '1.1rem 3rem',
                    borderRadius: 12,
                    textDecoration: 'none',
                    fontSize: '1.1rem',
                    fontWeight: 700,
                }}>
                    Create Your First Portfolio
                </Link>
            </section>

            <footer style={{ textAlign: 'center', padding: '2.5rem', color: '#4a4a4c', borderTop: '1px solid #1e1e20', fontSize: '0.85rem' }}>
                &copy; {new Date().getFullYear()} SpringHi.ai — Paper trading for education and competition purposes only. Not investment advice.
            </footer>
        </div>
    );
};

export default Home;
