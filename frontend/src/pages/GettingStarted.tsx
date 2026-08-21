import React from 'react';
import { Link } from 'react-router-dom';
import { 
    UserPlus, 
    Layout, 
    Wallet, 
    Cpu, 
    Zap, 
    Trophy, 
    ArrowRight, 
    CheckCircle2 
} from 'lucide-react';
import { getLoggedInUsername } from '../utils/auth';

const isLoggedIn = () => !!localStorage.getItem('token');

const GettingStarted: React.FC = () => {
    const username = getLoggedInUsername();
    const steps = [
        {
            icon: <UserPlus size={32} color="#60a5fa" />,
            title: "1. Create Your Account",
            description: "Sign up for free in seconds. No credit card or financial information is required — ever.",
            link: "/signup",
            linkText: "Sign Up Now"
        },
        {
            icon: <Layout size={32} color="#a78bfa" />,
            title: "2. Build Your Investor Profile",
            description: "Tell the AI about your goals. Set your risk tolerance, time horizon, and preferred market sectors.",
            link: "/profile",
            linkText: "Configure Profile"
        },
        {
            icon: <Wallet size={32} color="#34d399" />,
            title: "3. Create a Paper Portfolio",
            description: "Open your first portfolio and deposit paper cash. You can run multiple portfolios to test different strategies.",
            link: "/portfolio",
            linkText: "Open Portfolio"
        },
        {
            icon: <Cpu size={32} color="#f59e0b" />,
            title: "4. Run AI Optimization",
            description: "Choose between Claude, ChatGPT, or Gemini. The AI analyzes your profile and generates a buy/sell plan.",
            link: "/portfolio",
            linkText: "Try AI Now"
        },
        {
            icon: <Zap size={32} color="#f87171" />,
            title: "5. Execute Recommendations",
            description: "Review the AI's suggestions and execute them with one click. Sells happen first to ensure you have cash for buys.",
            link: "/portfolio",
            linkText: "View Trades"
        },
        {
            icon: <Trophy size={32} color="#22c55e" />,
            title: "6. Compete & Analyze",
            description: "Track your Time-Weighted Return (TWR) and see how you rank against the S&P 500 and other users.",
            link: "/leaderboard",
            linkText: "View Leaderboard"
        }
    ];

    return (
        <div style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>
            <header className="navbar">
                <div className="navbar-brand" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome" style={{ fontSize: '0.75rem', marginTop: '-0.2rem', opacity: 0.8 }}>Welcome back, {username}</span>}
                </div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Link to="/" className="nav-link">Home</Link>
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

            <main style={{ padding: '5rem 10% 8rem' }}>
                <div style={{ textAlign: 'center', marginBottom: '5rem' }}>
                    <h1 style={{
                        fontSize: 'clamp(2.5rem, 6vw, 4rem)',
                        fontWeight: 900,
                        lineHeight: 1.1,
                        marginBottom: '1.5rem',
                        background: 'linear-gradient(100deg, #fff 0%, #60a5fa 60%, #a78bfa 100%)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                    }}>
                        Get Started in Minutes
                    </h1>
                    <p style={{ fontSize: '1.2rem', color: '#a0a0a0', maxWidth: 700, margin: '0 auto', lineHeight: 1.7 }}>
                        Follow these six steps to launch your first AI-managed paper portfolio and start competing on the leaderboard.
                    </p>
                </div>

                <div style={{ 
                    display: 'grid', 
                    gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', 
                    gap: '2rem',
                    maxWidth: 1200,
                    margin: '0 auto'
                }}>
                    {steps.map((step, index) => (
                        <div key={index} style={{
                            background: '#161618',
                            border: '1px solid #2a2a2c',
                            borderRadius: 20,
                            padding: '2.5rem',
                            display: 'flex',
                            flexDirection: 'column',
                            position: 'relative',
                            transition: 'transform 0.2s ease, border-color 0.2s ease',
                            cursor: 'default'
                        }}>
                            <div style={{ marginBottom: '1.5rem' }}>{step.icon}</div>
                            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1rem', color: '#fff' }}>{step.title}</h2>
                            <p style={{ color: '#a0a0a0', lineHeight: 1.6, marginBottom: '2rem', flexGrow: 1 }}>{step.description}</p>
                            
                            <Link to={step.link} style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '0.5rem',
                                color: '#60a5fa',
                                textDecoration: 'none',
                                fontWeight: 600,
                                fontSize: '0.95rem'
                            }}>
                                {step.linkText} <ArrowRight size={16} />
                            </Link>
                        </div>
                    ))}
                </div>

                <section style={{
                    marginTop: '6rem',
                    background: 'linear-gradient(135deg, rgba(96,165,250,0.1) 0%, rgba(167,139,250,0.1) 100%)',
                    border: '1px solid rgba(96,165,250,0.2)',
                    borderRadius: 24,
                    padding: '4rem 3rem',
                    textAlign: 'center'
                }}>
                    <h2 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: '1.5rem' }}>Ready to beat the market?</h2>
                    <p style={{ color: '#a0a0a0', maxWidth: 600, margin: '0 auto 2.5rem', lineHeight: 1.7 }}>
                        Join thousands of investors using AI to experiment, learn, and compete. Completely free, completely transparent.
                    </p>
                    <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
                        <Link to="/signup" style={{
                            background: '#0066ff',
                            color: '#fff',
                            padding: '1rem 2.5rem',
                            borderRadius: 12,
                            textDecoration: 'none',
                            fontWeight: 700,
                        }}>
                            Create Free Account
                        </Link>
                        <Link to="/about" style={{
                            background: 'transparent',
                            color: '#fff',
                            padding: '1rem 2.5rem',
                            borderRadius: 12,
                            textDecoration: 'none',
                            fontWeight: 600,
                            border: '1px solid #3a3a3c',
                        }}>
                            Learn How It Works
                        </Link>
                    </div>
                </section>

                <div style={{ marginTop: '5rem', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '2rem', textAlign: 'center' }}>
                    {[
                        "No real money at risk",
                        "Real-time market data",
                        "Full strategy transparency",
                        "Multiple AI models"
                    ].map((text, i) => (
                        <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.75rem', color: '#a0a0a0', fontSize: '0.9rem' }}>
                            <CheckCircle2 size={18} color="#34d399" />
                            {text}
                        </div>
                    ))}
                </div>
            </main>
        </div>
    );
};

export default GettingStarted;
