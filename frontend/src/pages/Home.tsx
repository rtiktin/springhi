import React from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, Trophy, Eye, Cpu, BarChart2, CalendarClock } from 'lucide-react';

const isLoggedIn = () => !!localStorage.getItem('token');

const Home: React.FC = () => {
    return (
        <div className="home-container" style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>

            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
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
                <p style={{ fontSize: '1.2rem', color: '#a0a0a0', maxWidth: 680, margin: '0 auto 2.5rem', lineHeight: 1.7 }}>
                    Build AI-powered portfolios, compete against real market benchmarks and other investors,
                    and study every top performer's exact strategy — completely in the open.
                </p>
                <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
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
                            icon: <Cpu size={32} color="#60a5fa" />,
                            title: 'Multi-Model AI Optimization',
                            body: 'Choose Claude, ChatGPT, or Gemini to generate buy/sell recommendations tailored to your risk profile, goals, and time horizon. Set it to run on a schedule — daily, weekly, or monthly — and let AI rebalance automatically.',
                        },
                        {
                            icon: <Trophy size={32} color="#f59e0b" />,
                            title: 'Monthly Competitions',
                            body: 'Every portfolio competes in monthly leaderboards against peers and the S&P 500. See raw returns and your margin vs. the benchmark across 1W, 1M, 3M, 6M, and 1Y timeframes. Fresh competition every month.',
                        },
                        {
                            icon: <Eye size={32} color="#a78bfa" />,
                            title: 'Radical Transparency',
                            body: 'See the exact AI prompt, every recommendation, and every trade behind the #1 portfolio on the leaderboard. No black boxes. Copy the top strategy, improve it, and compete against it.',
                        },
                        {
                            icon: <TrendingUp size={32} color="#22c55e" />,
                            title: 'Professional Performance Tracking',
                            body: 'Time-Weighted Return (TWR) — the same methodology used by institutional fund managers — measures your true performance independent of cash deposits. Always know if you\'re beating the market.',
                        },
                        {
                            icon: <BarChart2 size={32} color="#f472b6" />,
                            title: 'Real Market Data',
                            body: 'Click any holding to see a full year of price history. Live prices update throughout the trading day. Your paper portfolio tracks real market movements in real time.',
                        },
                        {
                            icon: <CalendarClock size={32} color="#34d399" />,
                            title: 'Scheduled Auto-Optimization',
                            body: 'Set your AI to rebalance on a schedule and walk away. The system executes trades automatically on market-open days. Experiment with different rebalancing frequencies and see which strategy wins.',
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
