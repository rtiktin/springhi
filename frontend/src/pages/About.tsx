import React from 'react';
import { Link } from 'react-router-dom';
import { Trophy, Eye, Cpu, TrendingUp, BarChart2, CalendarClock, ShieldCheck, BookOpen, FlaskConical, GitCompare, ShieldAlert } from 'lucide-react';

const isLoggedIn = () => !!localStorage.getItem('token');

const About: React.FC = () => {
    return (
        <div style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>

            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Link to="/" className="nav-link">Home</Link>
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

            <main style={{ padding: '5rem 10%' }}>

                <div style={{ textAlign: 'center', marginBottom: '5rem' }}>
                    <h1 style={{
                        fontSize: 'clamp(2rem, 5vw, 3.5rem)',
                        fontWeight: 900,
                        lineHeight: 1.15,
                        background: 'linear-gradient(100deg, #fff 0%, #60a5fa 60%, #a78bfa 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        marginBottom: '1.25rem',
                    }}>
                        About SpringHi.ai
                    </h1>
                    <p style={{ fontSize: '1.15rem', color: '#a0a0a0', maxWidth: 700, margin: '0 auto', lineHeight: 1.75 }}>
                        SpringHi.ai is a risk-free investment learning and competition platform. You build
                        AI-powered portfolios with real market data, study every top performer's exact strategy,
                        and run experiments you could never safely run with real money — all in the open.
                        No real money. No regulatory complexity. Just learning, strategy, and AI.
                    </p>
                </div>

                <section style={{
                    background: '#161618',
                    border: '1px solid #2a2a2c',
                    borderRadius: 16,
                    padding: '2.5rem 3rem',
                    marginBottom: '4rem',
                }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '0.5rem' }}>What makes this different</h2>
                    <p style={{ color: '#a0a0a0', marginBottom: '2rem', lineHeight: 1.7 }}>
                        Most investment platforms are black boxes. You see someone's returns on a leaderboard
                        but have no idea how they got there. SpringHi.ai is the opposite.
                    </p>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1.25rem' }}>
                        {[
                            {
                                icon: <Eye size={24} color="#a78bfa" />,
                                point: 'Full strategy visibility',
                                desc: 'See the exact AI prompt, every recommendation, and every trade behind any portfolio on the leaderboard.',
                            },
                            {
                                icon: <Cpu size={24} color="#60a5fa" />,
                                point: 'Three AI models to choose from',
                                desc: 'Claude, ChatGPT, and Gemini each bring different reasoning styles. Pick one — or run all three and compare.',
                            },
                            {
                                icon: <Trophy size={24} color="#f59e0b" />,
                                point: 'Real competition, zero risk',
                                desc: 'Monthly leaderboards with S&P 500 as the benchmark. Paper trading means no real money changes hands.',
                            },
                            {
                                icon: <TrendingUp size={24} color="#22c55e" />,
                                point: 'Institutional-grade measurement',
                                desc: 'Time-Weighted Return (TWR) — the same methodology used by professional fund managers — so performance is always meaningful.',
                            },
                        ].map(item => (
                            <div key={item.point} style={{
                                background: '#0e0e10',
                                border: '1px solid #2a2a2c',
                                borderRadius: 12,
                                padding: '1.25rem',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '0.5rem',
                            }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                                    {item.icon}
                                    <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>{item.point}</span>
                                </div>
                                <p style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.65, margin: 0 }}>{item.desc}</p>
                            </div>
                        ))}
                    </div>
                </section>

                <section style={{ marginBottom: '4rem' }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '0.5rem', textAlign: 'center' }}>Four things you'll actually learn</h2>
                    <p style={{ color: '#a0a0a0', textAlign: 'center', marginBottom: '2rem', fontSize: '0.95rem' }}>
                        SpringHi.ai is built around questions most investors never get to answer safely.
                    </p>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1.25rem', marginBottom: '4rem' }}>
                        {[
                            {
                                icon: <BookOpen size={26} color="#a78bfa" />,
                                heading: 'What does the winning strategy actually look like?',
                                body: 'Every portfolio on the leaderboard is fully transparent. Open the #1 portfolio and read the exact AI prompt used, the risk profile, every recommendation generated, and every trade executed. Then replicate it, modify it, and try to beat it.',
                            },
                            {
                                icon: <ShieldAlert size={26} color="#f87171" />,
                                heading: 'How does risk tolerance actually shape a portfolio?',
                                body: 'Build an aggressive portfolio and a conservative one with the same starting cash. Watch how the AI responds to each — different sectors, different concentration, different conviction. Feel the difference in volatility over time, all without real consequences.',
                            },
                            {
                                icon: <CalendarClock size={26} color="#34d399" />,
                                heading: 'Does rebalancing frequency matter?',
                                body: 'Run daily, weekly, and monthly auto-optimizations in parallel and compare their Time-Weighted Returns side by side. There\'s no safer way to answer a question that professional fund managers debate constantly.',
                            },
                            {
                                icon: <GitCompare size={26} color="#60a5fa" />,
                                heading: 'Do different AI models pick differently?',
                                body: 'Claude, ChatGPT, and Gemini each have distinct reasoning styles. Give them the same profile and cash amount and study how they diverge in sector allocation, concentration, and the rationale behind each pick.',
                            },
                        ].map(item => (
                            <div key={item.heading} style={{
                                background: '#0e0e10',
                                border: '1px solid #2a2a2c',
                                borderRadius: 14,
                                padding: '1.5rem',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '0.6rem',
                            }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                                    {item.icon}
                                    <span style={{ fontWeight: 700, fontSize: '0.92rem', color: '#fff' }}>{item.heading}</span>
                                </div>
                                <p style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.65, margin: 0 }}>{item.body}</p>
                            </div>
                        ))}
                    </div>
                </section>

                <section style={{ marginBottom: '4rem' }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '2rem', textAlign: 'center' }}>Everything included</h2>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(290px, 1fr))', gap: '1.25rem' }}>
                        {[
                            {
                                icon: <CalendarClock size={28} color="#34d399" />,
                                title: 'Scheduled Auto-Optimization',
                                body: 'Set a daily, weekly, monthly, quarterly, or yearly schedule and the AI rebalances your portfolio automatically on market-open days — no action needed.',
                            },
                            {
                                icon: <BarChart2 size={28} color="#f472b6" />,
                                title: 'Real Market Data & Price History',
                                body: 'Live prices update throughout the trading day. Click any holding to see a full year of price history. Your paper portfolio tracks real market movements in real time.',
                            },
                            {
                                icon: <Trophy size={28} color="#f59e0b" />,
                                title: 'Monthly & All-Time Leaderboards',
                                body: 'Every portfolio automatically enters monthly and all-time leaderboards. Benchmark margin (your return vs. SPY) is shown alongside raw TWR for every timeframe.',
                            },
                            {
                                icon: <Eye size={28} color="#a78bfa" />,
                                title: 'Open AI Optimization History',
                                body: "Every AI run is permanently recorded and publicly visible — the profile used, the model chosen, the recommendations generated, and which trades were executed.",
                            },
                            {
                                icon: <Cpu size={28} color="#60a5fa" />,
                                title: 'Profile-Driven Recommendations',
                                body: 'Set your risk tolerance, investment goal, time horizon, liquidity needs, and preferred sectors. The AI tailors every recommendation to your specific profile.',
                            },
                            {
                                icon: <ShieldCheck size={28} color="#22c55e" />,
                                title: 'Secure & Private',
                                body: 'Email and phone verification on account creation. JWT-based authentication. Your data is never shared or sold.',
                            },
                        ].map(card => (
                            <div key={card.title} style={{
                                background: '#161618',
                                border: '1px solid #2a2a2c',
                                borderRadius: 14,
                                padding: '1.75rem',
                            }}>
                                <div style={{ marginBottom: '0.75rem' }}>{card.icon}</div>
                                <h3 style={{ fontSize: '1rem', fontWeight: 700, marginBottom: '0.5rem' }}>{card.title}</h3>
                                <p style={{ color: '#a0a0a0', fontSize: '0.875rem', lineHeight: 1.65, margin: 0 }}>{card.body}</p>
                            </div>
                        ))}
                    </div>
                </section>

                <section style={{
                    background: '#161618',
                    border: '1px solid #2a2a2c',
                    borderRadius: 16,
                    padding: '2.5rem 3rem',
                    marginBottom: '4rem',
                }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '1.75rem' }}>How it works</h2>
                    <ol style={{ margin: 0, paddingLeft: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                        {[
                            ['Create an account', 'Sign up for free — no credit card required.'],
                            ['Build your investor profile', 'Set your risk tolerance, goals, time horizon, and sector preferences.'],
                            ['Create a portfolio & add cash', 'Name your portfolio and deposit paper money to invest with.'],
                            ['Run AI optimization', 'Choose Claude, ChatGPT, or Gemini. The AI generates a personalized buy/sell plan.'],
                            ['Execute trades', 'Execute recommendations individually or all at once. Sells run first to free up cash for buys.'],
                            ['Compete & study', 'See your TWR vs. the S&P 500 on the leaderboard — and explore any competitor\'s full strategy.'],
                        ].map(([title, desc]) => (
                            <li key={title} style={{ lineHeight: 1.6 }}>
                                <strong style={{ color: '#fff' }}>{title}</strong>
                                <span style={{ color: '#a0a0a0' }}> — {desc}</span>
                            </li>
                        ))}
                    </ol>
                </section>

                <section style={{
                    background: 'linear-gradient(135deg, rgba(0,102,255,0.12) 0%, rgba(167,139,250,0.1) 100%)',
                    border: '1px solid rgba(0,102,255,0.25)',
                    borderRadius: 16,
                    padding: '3rem',
                    marginBottom: '4rem',
                    textAlign: 'center',
                }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '0.75rem' }}>Paper trading — by design</h2>
                    <p style={{ color: '#a0a0a0', maxWidth: 660, margin: '0 auto', lineHeight: 1.75 }}>
                        Real investing doesn't let you run controlled experiments. SpringHi.ai does.
                        Want to know if an aggressive AI-managed portfolio beats a conservative one over six months?
                        Run both. Want to know if daily rebalancing beats monthly? Run both. Want to know which AI model
                        picks better for a growth-focused, high-risk profile? Run all three simultaneously.
                        No financial consequence. No regulatory friction. Just clean data and honest answers.
                    </p>
                </section>

                <div style={{ textAlign: 'center' }}>
                    {isLoggedIn() ? (
                        <Link to="/portfolio" className="btn-primary-large">Go to My Portfolio</Link>
                    ) : (
                        <Link to="/signup" className="btn-primary-large">Create Your First Portfolio</Link>
                    )}
                </div>
            </main>

            <footer style={{ textAlign: 'center', padding: '2.5rem', color: '#4a4a4c', borderTop: '1px solid #1e1e20', fontSize: '0.85rem', marginTop: '4rem' }}>
                &copy; {new Date().getFullYear()} SpringHi.ai — Paper trading for education and competition purposes only. Not investment advice.
            </footer>
        </div>
    );
};

export default About;
