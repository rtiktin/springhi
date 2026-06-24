import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import API_GATEWAY from '../api/apiBase';

type Step = 'email' | 'code';

const ForgotPassword: React.FC = () => {
    const navigate = useNavigate();
    const [step, setStep] = useState<Step>('email');
    const [email, setEmail] = useState('');
    const [code, setCode] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');

    const handleSendCode = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const res = await axios.post(`${API_GATEWAY}/api/v1/auth/forgot-password`, { email: email.trim() });
            setInfo(res.data.message);
            setStep('code');
        } catch {
            setError('Failed to send reset code. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const handleResetPassword = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        if (!/^\d{6}$/.test(code.trim())) {
            setError('Please enter the 6-digit numeric code sent to your email.');
            return;
        }
        if (newPassword.length < 8) {
            setError('Password must be at least 8 characters long.');
            return;
        }
        if (newPassword !== confirmPassword) {
            setError('Passwords do not match.');
            return;
        }
        setLoading(true);
        try {
            await axios.post(`${API_GATEWAY}/api/v1/auth/reset-password`, {
                email: email.trim(),
                code: code.trim(),
                newPassword,
            });
            navigate('/login', { state: { message: 'Password reset successfully. Please log in.' } });
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setError(msg ?? 'Failed to reset password. Please check your code and try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-card">
                <Link to="/" className="logo-text">SpringHi.ai</Link>
                <h2>Reset Password</h2>

                {step === 'email' && (
                    <>
                        <p>Enter the email address on your account. We'll send you a 6-digit code.</p>
                        {error && <div className="error-msg">{error}</div>}
                        <form onSubmit={handleSendCode}>
                            <input
                                type="email"
                                placeholder="Email address"
                                value={email}
                                onChange={e => setEmail(e.target.value)}
                                required
                                autoFocus
                            />
                            <button type="submit" className="btn-primary-full" disabled={loading}>
                                {loading ? 'Sending…' : 'Send Reset Code'}
                            </button>
                        </form>
                    </>
                )}

                {step === 'code' && (
                    <>
                        <p style={{ color: 'var(--text-gray)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>
                            {info || `A 6-digit code was sent to ${email}. It expires in 15 minutes.`}
                        </p>
                        {error && <div className="error-msg">{error}</div>}
                        <form onSubmit={handleResetPassword}>
                            <input
                                type="text"
                                placeholder="6-digit code"
                                value={code}
                                onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                                maxLength={6}
                                inputMode="numeric"
                                autoFocus
                                style={{ letterSpacing: '0.3em', textAlign: 'center', fontSize: '1.2rem' }}
                            />
                            <div className="password-input-wrap">
                                <input
                                    type={showPassword ? 'text' : 'password'}
                                    placeholder="New password (min 8 characters)"
                                    value={newPassword}
                                    onChange={e => setNewPassword(e.target.value)}
                                />
                                <button type="button" className="show-password-btn" onClick={() => setShowPassword(v => !v)}>
                                    {showPassword ? 'Hide' : 'Show'}
                                </button>
                            </div>
                            <input
                                type={showPassword ? 'text' : 'password'}
                                placeholder="Confirm new password"
                                value={confirmPassword}
                                onChange={e => setConfirmPassword(e.target.value)}
                            />
                            <button type="submit" className="btn-primary-full" disabled={loading}>
                                {loading ? 'Resetting…' : 'Reset Password'}
                            </button>
                        </form>
                        <p style={{ marginTop: '0.75rem', fontSize: '0.85rem', color: 'var(--text-gray)' }}>
                            Didn't receive the code?{' '}
                            <button
                                type="button"
                                onClick={() => { setStep('email'); setError(''); setCode(''); }}
                                style={{ background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', padding: 0, fontSize: 'inherit' }}
                            >
                                Resend
                            </button>
                        </p>
                    </>
                )}

                <p className="auth-footer">
                    <Link to="/login">Back to Log In</Link>
                </p>
            </div>
        </div>
    );
};

export default ForgotPassword;
