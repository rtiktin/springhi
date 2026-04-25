import React from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp, ShieldCheck, Cpu } from 'lucide-react';

const isLoggedIn = () => !!localStorage.getItem('token');

const Home: React.FC = () => {
  return (
    <div className="home-container">
      <header className="navbar">
        <div className="logo">SpringHi.ai</div>
        <nav>
          <Link to="/about" className="nav-link">About</Link>
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

      <main className="hero">
        <h1>AI-Powered Portfolio Management</h1>
        <p>Next-generation wealth management driven by advanced machine learning algorithms. Secure your future with Springhi.</p>
        <div className="hero-btns">
          <Link to="/signup" className="btn-primary-large">Start Investing Now</Link>
        </div>
      </main>

      <section className="features">
        <div className="feature-card">
          <Cpu size={48} className="icon" />
          <h3>AI Optimization</h3>
          <p>Real-time rebalancing and risk assessment using neural networks.</p>
        </div>
        <div className="feature-card">
          <TrendingUp size={48} className="icon" />
          <h3>Maximum Returns</h3>
          <p>Data-driven insights to capture market opportunities faster than human traders.</p>
        </div>
        <div className="feature-card">
          <ShieldCheck size={48} className="icon" />
          <h3>Secured Assets</h3>
          <p>Enterprise-grade security and encryption for your financial data.</p>
        </div>
      </section>

      <footer className="footer">
        <p>&copy; 2025 SpringHi.ai. All rights reserved.</p>
      </footer>
    </div>
  );
};

export default Home;
