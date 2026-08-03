import React, { useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import Home from './pages/Home';
import Login from './pages/Login';
import Signup from './pages/Signup';
import Portfolio from './pages/Portfolio';
import ProfileEdit from './pages/ProfileEdit';
import AccountMaintenance from './pages/AccountMaintenance';
import About from './pages/About';
import Leaderboard from './pages/Leaderboard';
import ForgotPassword from './pages/ForgotPassword';
import Admin from './pages/Admin';
import { isLoggedIn } from './utils/auth';
import API_GATEWAY from './api/apiBase';
import './App.css';

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
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/portfolio" element={<RequireAuth><Portfolio /></RequireAuth>} />
          <Route path="/profile" element={<RequireAuth><ProfileEdit /></RequireAuth>} />
          <Route path="/account" element={<RequireAuth><AccountMaintenance /></RequireAuth>} />
          <Route path="/about" element={<About />} />
          <Route path="/leaderboard" element={<RequireAuth><Leaderboard /></RequireAuth>} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/admin" element={<RequireAuth><Admin /></RequireAuth>} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
