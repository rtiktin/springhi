import React, { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { isLoggedIn } from './utils/auth';
import API_GATEWAY from './api/apiBase';
import './App.css';

const Home = lazy(() => import('./pages/Home'));
const GettingStarted = lazy(() => import('./pages/GettingStarted'));
const Login = lazy(() => import('./pages/Login'));
const Signup = lazy(() => import('./pages/Signup'));
const Portfolio = lazy(() => import('./pages/Portfolio'));
const ProfileEdit = lazy(() => import('./pages/ProfileEdit'));
const AccountMaintenance = lazy(() => import('./pages/AccountMaintenance'));
const About = lazy(() => import('./pages/About'));
const Leaderboard = lazy(() => import('./pages/Leaderboard'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const Admin = lazy(() => import('./pages/Admin'));
const Subscription = lazy(() => import('./pages/Subscription'));
const Pricing = lazy(() => import('./pages/Pricing'));
const Support = lazy(() => import('./pages/Support'));

const PageLoader: React.FC = () => (
  <div className="portfolio-loading">Loading…</div>
);

const RequireAuth: React.FC<{ children: React.ReactElement }> = ({ children }) => {
  return isLoggedIn() ? children : <Navigate to="/login" replace />;
};

const ActivityTracker: React.FC = () => {
  const location = useLocation();
  useEffect(() => {
    if (!isLoggedIn()) return;
    const today = new Date().toISOString().slice(0, 10);
    if (localStorage.getItem('lastActivityPing') === today) return;
    axios.put(
      `${API_GATEWAY}/api/v1/users/activity`,
      {},
      { headers: { Authorization: `Bearer ${localStorage.getItem('token')}` } }
    ).then(() => localStorage.setItem('lastActivityPing', today))
     .catch(() => {});
  }, [location.pathname]);
  return null;
};

function App() {
  return (
    <Router>
      <div className="App">
        <ActivityTracker />
        <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/getting-started" element={<GettingStarted />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/portfolio" element={<RequireAuth><Portfolio /></RequireAuth>} />
          <Route path="/profile" element={<RequireAuth><ProfileEdit /></RequireAuth>} />
          <Route path="/account" element={<RequireAuth><AccountMaintenance /></RequireAuth>} />
          <Route path="/about" element={<About />} />
          <Route path="/leaderboard" element={<RequireAuth><Leaderboard /></RequireAuth>} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/admin" element={<RequireAuth><Admin /></RequireAuth>} />
          <Route path="/subscription" element={<RequireAuth><Subscription /></RequireAuth>} />
          <Route path="/pricing" element={<Pricing />} />
          <Route path="/support" element={<RequireAuth><Support /></RequireAuth>} />
        </Routes>
        </Suspense>
      </div>
    </Router>
  );
}

export default App;
