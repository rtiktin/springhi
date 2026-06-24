import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';

const Login: React.FC = () => {
  const [formData, setFormData] = useState({
    username: '',
    password: ''
  });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const location = useLocation();
  const successMessage = (location.state as { message?: string })?.message;

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const response = await axios.post('http://localhost:9000/api/v1/auth/signin', formData);
      localStorage.setItem('token', response.data.token);
      navigate('/portfolio');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed. Please check your credentials.');
    }
  };

  return (
    <div className="auth-container">
      <div className="auth-card">
        <Link to="/" className="logo-text">SpringHi.ai</Link>
        <h2>Welcome Back</h2>
        <p>Log in to manage your AI portfolio.</p>

        {successMessage && <div className="success-msg" style={{ color: '#22c55e', marginBottom: '0.75rem', fontSize: '0.9rem' }}>{successMessage}</div>}
        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit}>
          <input type="text" name="username" placeholder="Username" onChange={handleChange} required />
          <div className="password-input-wrap">
            <input
              type={showPassword ? 'text' : 'password'}
              name="password"
              placeholder="Password"
              onChange={handleChange}
              required
            />
            <button type="button" className="show-password-btn" onClick={() => setShowPassword(v => !v)}>
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
          <button type="submit" className="btn-primary-full">Log In</button>
        </form>

        <p style={{ marginTop: '0.5rem', fontSize: '0.85rem', textAlign: 'right' }}>
          <Link to="/forgot-password" style={{ color: 'var(--text-gray)' }}>Forgot password?</Link>
        </p>

        <p className="auth-footer">
          Don't have an account? <Link to="/signup">Sign up</Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
