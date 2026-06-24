import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Home from './pages/Home';
import Login from './pages/Login';
import Signup from './pages/Signup';
import Portfolio from './pages/Portfolio';
import ProfileEdit from './pages/ProfileEdit';
import AccountMaintenance from './pages/AccountMaintenance';
import About from './pages/About';
import ForgotPassword from './pages/ForgotPassword';
import { isLoggedIn } from './utils/auth';
import './App.css';

const RequireAuth: React.FC<{ children: React.ReactElement }> = ({ children }) => {
  return isLoggedIn() ? children : <Navigate to="/login" replace />;
};

function App() {
  return (
    <Router>
      <div className="App">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/portfolio" element={<RequireAuth><Portfolio /></RequireAuth>} />
          <Route path="/profile" element={<RequireAuth><ProfileEdit /></RequireAuth>} />
          <Route path="/account" element={<RequireAuth><AccountMaintenance /></RequireAuth>} />
          <Route path="/about" element={<About />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
