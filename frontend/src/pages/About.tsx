import React from 'react';
import { Link } from 'react-router-dom';
import { Cpu, TrendingUp, ShieldCheck, BarChart2, DollarSign, RefreshCw } from 'lucide-react';

const isLoggedIn = () => !!localStorage.getItem('token');

const About: React.FC = () => {
    return (
        <div className="home-container">
            <header className="navbar">
                <div className="logo">SpringHi.ai</div>
                <nav>
                    <Link to="/" className="nav-link">Home</Link>
                    {isLoggedIn() ? (
                        <Link to="/portfolio" className="btn-primary">My Portfolio</Link>
                    ) : (
                        <>
                            <Link to="/login" className="nav-link">Login</Link>
                            <Link to="/signup" className="btn-primary">Get Started</Link>
                        </>
                    )}
                </nav>
            </header>

            <main style={{ padding: '5rem 10%' }}>
                <div style={{ textAlign: 'center', marginBottom: '4rem' }}>
                    <h1 style={{
                        fontSize: '3rem',
                        fontWeight: 800,
                        background: 'linear-gradient(90deg, #fff 0%, #0066ff 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        marginBottom: '1rem',
                    }}>
                        About SpringHi.ai
                    </h1>
                    <p style={{ fontSize: '1.2rem', color: 'var(--text-gray)', maxWidth: '700px', margin: '0 auto' }}>
                        A modern, AI-driven portfolio management platform that helps investors make smarter decisions,
                        rebalance their holdings, and track performance — all in one place.
                    </p>
                </div>

                <section className="features" style={{ padding: '0 0 4rem' }}>
                    <div className="feature-card">
                        <Cpu size={40} className="icon" />
                        <h3>AI Portfolio Optimization</h3>
                        <p>
                            Powered by Google Gemini, SpringHi.ai analyzes your risk profile, investment goals,
                            time horizon, and current holdings to generate a personalized rebalancing plan with
                            specific buy and sell recommendations.
                        </p>
                    </div>
                    <div className="feature-card">
                        <TrendingUp size={40} className="icon" />
                        <h3>Real-Time Market Data</h3>
                        <p>
                            Market quotes are sourced in real time, giving you
                            accurate pricing for your holdings and trade decisions whenever you need it.
                        </p>
                    </div>
                    <div className="feature-card">
                        <RefreshCw size={40} className="icon" />
                        <h3>Portfolio Rebalancing</h3>
                        <p>
                            The optimizer accounts for your existing positions and suggests targeted sells
                            and buys to bring your portfolio in line with your stated goals. Sells are
                            executed first to free up cash for new positions.
                        </p>
                    </div>
                    <div className="feature-card">
                        <BarChart2 size={40} className="icon" />
                        <h3>Performance History</h3>
                        <p>
                            Daily portfolio snapshots capture your total value over time, letting you
                            visualize growth trends through an interactive chart on your dashboard.
                        </p>
                    </div>
                    <div className="feature-card">
                        <DollarSign size={40} className="icon" />
                        <h3>Cash Management</h3>
                        <p>
                            Track your available investment cash with deposit and withdrawal transactions.
                            The platform enforces cash availability before executing any buy trades,
                            keeping your account in balance.
                        </p>
                    </div>
                    <div className="feature-card">
                        <ShieldCheck size={40} className="icon" />
                        <h3>Secure & Private</h3>
                        <p>
                            All data is protected with JWT-based authentication and enterprise-grade
                            security. Your financial information stays yours — never shared or sold.
                        </p>
                    </div>
                </section>

                <section style={{
                    background: 'var(--card-bg)',
                    border: '1px solid #2a2a2c',
                    borderRadius: '16px',
                    padding: '3rem',
                    marginBottom: '4rem',
                }}>
                    <h2 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '1.5rem' }}>How It Works</h2>
                    <ol style={{ paddingLeft: '1.5rem', lineHeight: '2.2rem', color: 'var(--text-gray)', fontSize: '1rem' }}>
                        <li><strong style={{ color: 'white' }}>Create an account</strong> — sign up and complete your investor profile with your risk tolerance, goals, and time horizon.</li>
                        <li><strong style={{ color: 'white' }}>Deposit cash</strong> — add funds to your investment account to get started.</li>
                        <li><strong style={{ color: 'white' }}>Run the optimizer</strong> — click Optimize on the portfolio page to get AI-generated buy and sell recommendations tailored to your profile.</li>
                        <li><strong style={{ color: 'white' }}>Execute sells first</strong> — sell recommended positions to free up cash, then review updated buy estimates.</li>
                        <li><strong style={{ color: 'white' }}>Execute buys</strong> — purchase recommended securities using your available cash balance.</li>
                        <li><strong style={{ color: 'white' }}>Track performance</strong> — monitor your holdings, gains/losses, and portfolio growth over time.</li>
                    </ol>
                </section>

                <section style={{
                    background: 'var(--card-bg)',
                    border: '1px solid #2a2a2c',
                    borderRadius: '16px',
                    padding: '3rem',
                    marginBottom: '4rem',
                }}>
                    <h2 style={{ fontSize: '1.75rem', fontWeight: 700, marginBottom: '1.5rem' }}>Technology Stack</h2>
                    <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                        gap: '1.5rem',
                        color: 'var(--text-gray)',
                    }}>
                        {[
                            { label: 'Frontend', value: 'React 19, TypeScript, Vite' },
                            { label: 'Backend', value: 'Spring Boot 3, Spring Security, JWT' },
                            { label: 'Portfolio Service', value: 'Spring Boot WebFlux, Reactive' },
                            { label: 'API Gateway', value: 'Spring Cloud Gateway' },
                            { label: 'Database', value: 'PostgreSQL' },
                            { label: 'AI Engine', value: 'Google Gemini' },
                            { label: 'Market Data', value: 'Alpaca Markets API' },
                            { label: 'Charts', value: 'Lightweight Charts' },
                        ].map(({ label, value }) => (
                            <div key={label} style={{
                                background: '#1a1a1c',
                                borderRadius: '10px',
                                padding: '1rem 1.25rem',
                                border: '1px solid #2a2a2c',
                            }}>
                                <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: '0.4rem' }}>{label}</div>
                                <div style={{ color: 'white', fontWeight: 600 }}>{value}</div>
                            </div>
                        ))}
                    </div>
                </section>

                <div style={{ textAlign: 'center' }}>
                    {isLoggedIn() ? (
                        <Link to="/portfolio" className="btn-primary-large">Go to My Portfolio</Link>
                    ) : (
                        <Link to="/signup" className="btn-primary-large">Start Investing Now</Link>
                    )}
                </div>
            </main>

            <footer className="footer">
                <p>&copy; 2025 SpringHi.ai. All rights reserved.</p>
            </footer>
        </div>
    );
};

export default About;
