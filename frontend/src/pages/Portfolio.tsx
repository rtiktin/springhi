import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getLoggedInUsername } from '../utils/auth';
import PortfolioDashboard from '../components/PortfolioDashboard';
import TransactionHistory from '../components/TransactionHistory';
import TradeForm from '../components/TradeForm';
import CashForm from '../components/CashForm';
import OptimizePanel from '../components/OptimizePanel';

type Tab = 'holdings' | 'transactions' | 'optimize';

const Portfolio: React.FC = () => {
    const navigate = useNavigate();
    const username = getLoggedInUsername();
    const [activeTab, setActiveTab] = useState<Tab>('holdings');
    const [showTradeForm, setShowTradeForm] = useState(false);
    const [showCashForm, setShowCashForm] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    const handleTradeSuccess = () => {
        setRefreshKey(k => k + 1);
    };

    return (
        <div className="portfolio-page">
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/account" className="btn-logout">Account</Link>
                    <Link to="/profile" className="btn-logout">Investor Profile</Link>
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

                <div className="tab-bar">
                    <button
                        className={`tab-btn ${activeTab === 'holdings' ? 'tab-active' : ''}`}
                        onClick={() => setActiveTab('holdings')}
                    >
                        Holdings
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'transactions' ? 'tab-active' : ''}`}
                        onClick={() => setActiveTab('transactions')}
                    >
                        Transactions
                    </button>
                    <button
                        className={`tab-btn ${activeTab === 'optimize' ? 'tab-active' : ''}`}
                        onClick={() => setActiveTab('optimize')}
                    >
                        AI Optimize
                    </button>
                </div>

                {activeTab === 'holdings' && <PortfolioDashboard key={refreshKey} />}
                {activeTab === 'transactions' && <TransactionHistory key={refreshKey} />}
                {activeTab === 'optimize' && <OptimizePanel onTradeSuccess={handleTradeSuccess} />}
            </main>

            {showTradeForm && (
                <TradeForm
                    onClose={() => setShowTradeForm(false)}
                    onSuccess={handleTradeSuccess}
                />
            )}

            {showCashForm && (
                <CashForm
                    onClose={() => setShowCashForm(false)}
                    onSuccess={handleTradeSuccess}
                />
            )}
        </div>
    );
};

export default Portfolio;
