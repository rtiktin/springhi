import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getLoggedInUsername, isAdmin, isEmailVerified, isPhoneVerified } from '../utils/auth';
import ImpersonationBanner from '../components/ImpersonationBanner';
import { getAccountProfile, updateAccountProfile, sendEmailVerification, verifyEmail, sendPhoneVerification, verifyPhone } from '../api/accountApi';
import type { AccountProfile } from '../api/accountApi';
import CashForm from '../components/CashForm';
import { getOrCreateDefaultPortfolio } from '../api/portfolioApi';

const empty: AccountProfile = {
    firstName: '', lastName: '', bio: '', phone: '',
    addressLine1: '', addressLine2: '', city: '', state: '',
    postalCode: '', country: 'US', dateOfBirth: '', email: '',
};

type FieldErrors = Partial<Record<keyof AccountProfile, string>>;

const REQUIRED: (keyof AccountProfile)[] = ['firstName', 'lastName', 'addressLine1', 'city', 'state', 'postalCode', 'country'];
const REQUIRED_LABELS: Record<string, string> = {
    firstName: 'First Name', lastName: 'Last Name', addressLine1: 'Address Line 1',
    city: 'City', state: 'State / Province', postalCode: 'Postal Code', country: 'Country',
};

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validateForm(form: AccountProfile): FieldErrors {
    const errs: FieldErrors = {};

    for (const field of REQUIRED) {
        if (!form[field]?.toString().trim()) {
            errs[field] = `${REQUIRED_LABELS[field]} is required.`;
        }
    }

    if (form.email && !EMAIL_REGEX.test(form.email.trim())) {
        errs.email = 'Enter a valid email address.';
    }

    if (form.dateOfBirth) {
        const dob = new Date(form.dateOfBirth);
        if (isNaN(dob.getTime())) {
            errs.dateOfBirth = 'Enter a valid date.';
        } else {
            const today = new Date();
            const age = today.getFullYear() - dob.getFullYear()
                - (today < new Date(today.getFullYear(), dob.getMonth(), dob.getDate()) ? 1 : 0);
            if (dob >= today) {
                errs.dateOfBirth = 'Date of birth must be in the past.';
            } else if (age < 18) {
                errs.dateOfBirth = 'You must be at least 18 years old.';
            } else if (age > 120) {
                errs.dateOfBirth = 'Enter a valid date of birth.';
            }
        }
    }

    return errs;
}

const AccountMaintenance: React.FC = () => {
    const navigate = useNavigate();
    const username = getLoggedInUsername();
    const [form, setForm] = useState<AccountProfile>(empty);
    const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState('');
    const [showCashForm, setShowCashForm] = useState(false);
    const [defaultPortfolioId, setDefaultPortfolioId] = useState<number | null>(null);

    type UpdateEmailStep = 'enter-email' | 'enter-code';
    const [updateEmailStep, setUpdateEmailStep] = useState<UpdateEmailStep | null>(null);
    const [updateNewEmail, setUpdateNewEmail] = useState('');
    const [updateEmailCode, setUpdateEmailCode] = useState('');
    const [updateEmailSending, setUpdateEmailSending] = useState(false);
    const [updateEmailError, setUpdateEmailError] = useState('');

    type UpdatePhoneStep = 'enter-phone' | 'enter-code';
    const [updatePhoneStep, setUpdatePhoneStep] = useState<UpdatePhoneStep | null>(null);
    const [updateNewPhone, setUpdateNewPhone] = useState('');
    const [updatePhoneCode, setUpdatePhoneCode] = useState('');
    const [updatePhoneSending, setUpdatePhoneSending] = useState(false);
    const [updatePhoneError, setUpdatePhoneError] = useState('');

    useEffect(() => {
        getOrCreateDefaultPortfolio().then(p => setDefaultPortfolioId(p.id)).catch(() => {});
        getAccountProfile()
            .then(data => setForm(data))
            .catch((err) => {
                console.error('Failed to load account details:', err?.response?.status, err?.response?.data, err?.message);
                const status = err?.response?.status;
                if (status === 401 || status === 403) {
                    setError(`Failed to load account details (${status} – please log out and log in again).`);
                } else {
                    setError(`Failed to load account details${status ? ` (HTTP ${status})` : ''}.`);
                }
            })
            .finally(() => setLoading(false));
    }, []);

    const set = (field: keyof AccountProfile, value: string) => {
        setForm(prev => ({ ...prev, [field]: value }));
        if (fieldErrors[field]) {
            setFieldErrors(prev => ({ ...prev, [field]: undefined }));
        }
    };

    const handleSave = async () => {
        const errs = validateForm(form);
        if (Object.keys(errs).length > 0) {
            setFieldErrors(errs);
            setError('Please correct the errors below before saving.');
            return;
        }
        setFieldErrors({});
        setSaving(true);
        setError('');
        setSaved(false);
        try {
            const updated = await updateAccountProfile(form);
            setForm(updated);
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            if (msg) {
                setError(msg);
            } else {
                setError('Failed to save account details.');
            }
        } finally {
            setSaving(false);
        }
    };

    const handleSendUpdateCode = async () => {
        const email = updateNewEmail.trim();
        if (!email || !EMAIL_REGEX.test(email)) {
            setUpdateEmailError('Enter a valid email address.');
            return;
        }
        setUpdateEmailError('');
        setUpdateEmailSending(true);
        try {
            const resp = await sendEmailVerification(email);
            if (resp.token) localStorage.setItem('token', resp.token);
            setUpdateEmailStep('enter-code');
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setUpdateEmailError(msg || 'Failed to send verification code. Please try again.');
        } finally {
            setUpdateEmailSending(false);
        }
    };

    const handleVerifyUpdateCode = async () => {
        if (!updateEmailCode.trim()) { setUpdateEmailError('Please enter the verification code.'); return; }
        setUpdateEmailError('');
        setUpdateEmailSending(true);
        try {
            const resp = await verifyEmail(updateEmailCode.trim());
            if (resp.token) localStorage.setItem('token', resp.token);
            setForm(prev => ({ ...prev, email: updateNewEmail.trim() }));
            setUpdateEmailStep(null);
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setUpdateEmailError(msg || 'Invalid or expired code. Please try again.');
        } finally {
            setUpdateEmailSending(false);
        }
    };

    const handleSendUpdatePhoneCode = async () => {
        const phone = updateNewPhone.trim();
        if (!phone) { setUpdatePhoneError('Please enter a cell phone number.'); return; }
        setUpdatePhoneError('');
        setUpdatePhoneSending(true);
        try {
            const resp = await sendPhoneVerification(phone);
            if (resp.token) localStorage.setItem('token', resp.token);
            setUpdatePhoneStep('enter-code');
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setUpdatePhoneError(msg || 'Failed to send SMS code. Please try again.');
        } finally {
            setUpdatePhoneSending(false);
        }
    };

    const handleVerifyUpdatePhoneCode = async () => {
        if (!updatePhoneCode.trim()) { setUpdatePhoneError('Please enter the verification code.'); return; }
        setUpdatePhoneError('');
        setUpdatePhoneSending(true);
        try {
            const resp = await verifyPhone(updatePhoneCode.trim());
            if (resp.token) localStorage.setItem('token', resp.token);
            const normalized = updateNewPhone.trim().replace(/[\s\-\(\)]/g, '');
            setForm(prev => ({ ...prev, phone: normalized.startsWith('+') ? normalized : '+1' + normalized }));
            setUpdatePhoneStep(null);
        } catch (err: unknown) {
            const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setUpdatePhoneError(msg || 'Invalid or expired code. Please try again.');
        } finally {
            setUpdatePhoneSending(false);
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    if (loading) return <div className="portfolio-loading">Loading…</div>;

    return (
        <div className="portfolio-page">
            <ImpersonationBanner />
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/portfolio" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/portfolio" className="btn-logout">Portfolios</Link>
                    <Link to="/profile" className="btn-logout">Default Profile</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    {isAdmin() && <Link to="/admin" className="btn-logout">Admin</Link>}
                    <button className="btn-trade" onClick={() => setShowCashForm(true)}>$ Cash</button>
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <h1 className="portfolio-heading">Account Maintenance</h1>
                <p className="portfolio-sub">Manage your personal and contact information.</p>

                <div className="profile-form-card">

                    <div className="profile-section-title">Account Info</div>
                    <div className="acct-readonly-row">
                        <div className="acct-readonly-field">
                            <span className="form-label">Username</span>
                            <span className="acct-readonly-value">{form.username ?? '—'}</span>
                        </div>
                        <div className="acct-readonly-field">
                            <span className="form-label">Member Since</span>
                            <span className="acct-readonly-value">
                                {form.createdAt ? new Date(form.createdAt).toLocaleDateString() : '—'}
                            </span>
                        </div>
                    </div>
                    <div className="profile-field" style={{ marginTop: '0.75rem', marginBottom: '0.75rem' }}>
                        <label className="form-label">Email Address</label>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                            <span className="acct-readonly-value">{form.email ?? '—'}</span>
                            {isEmailVerified() && (
                                <button
                                    className="btn-primary"
                                    style={{ fontSize: '0.82rem', padding: '0.35rem 0.9rem' }}
                                    onClick={() => {
                                        setUpdateNewEmail('');
                                        setUpdateEmailCode('');
                                        setUpdateEmailError('');
                                        setUpdateEmailStep('enter-email');
                                    }}
                                >
                                    Update Email Address
                                </button>
                            )}
                        </div>
                    </div>

                    <div className="profile-section-title" style={{ marginTop: '1.5rem' }}>Personal Details</div>
                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">First Name <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.firstName ? ' input-error' : ''}`} value={form.firstName ?? ''} onChange={e => set('firstName', e.target.value)} />
                            {fieldErrors.firstName && <span className="field-error-msg">{fieldErrors.firstName}</span>}
                        </div>
                        <div className="profile-field">
                            <label className="form-label">Last Name <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.lastName ? ' input-error' : ''}`} value={form.lastName ?? ''} onChange={e => set('lastName', e.target.value)} />
                            {fieldErrors.lastName && <span className="field-error-msg">{fieldErrors.lastName}</span>}
                        </div>
                    </div>
                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">Date of Birth</label>
                            <input type="date" className={`profile-input${fieldErrors.dateOfBirth ? ' input-error' : ''}`} value={form.dateOfBirth ?? ''} onChange={e => set('dateOfBirth', e.target.value)} />
                            {fieldErrors.dateOfBirth && <span className="field-error-msg">{fieldErrors.dateOfBirth}</span>}
                        </div>
                        <div className="profile-field">
                            <label className="form-label">Cell Phone Number</label>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                                <span className="acct-readonly-value">{form.phone ?? '—'}</span>
                                {isPhoneVerified() && (
                                    <button
                                        className="btn-primary"
                                        style={{ fontSize: '0.82rem', padding: '0.35rem 0.9rem' }}
                                        onClick={() => {
                                            setUpdateNewPhone('');
                                            setUpdatePhoneCode('');
                                            setUpdatePhoneError('');
                                            setUpdatePhoneStep('enter-phone');
                                        }}
                                    >
                                        Update Cell Phone Number
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                    <div className="profile-field" style={{ marginBottom: '0.75rem' }}>
                        <label className="form-label">Bio</label>
                        <textarea
                            className="profile-textarea"
                            rows={3}
                            value={form.bio ?? ''}
                            placeholder="A short description about yourself…"
                            onChange={e => set('bio', e.target.value)}
                        />
                    </div>

                    <div className="profile-section-title" style={{ marginTop: '1.5rem' }}>Address</div>
                    <div className="profile-field" style={{ marginBottom: '0.75rem' }}>
                        <label className="form-label">Address Line 1 <span className="field-required">*</span></label>
                        <input className={`profile-input${fieldErrors.addressLine1 ? ' input-error' : ''}`} value={form.addressLine1 ?? ''} placeholder="Street address" onChange={e => set('addressLine1', e.target.value)} />
                        {fieldErrors.addressLine1 && <span className="field-error-msg">{fieldErrors.addressLine1}</span>}
                    </div>
                    <div className="profile-field" style={{ marginBottom: '0.75rem' }}>
                        <label className="form-label">Address Line 2</label>
                        <input className="profile-input" value={form.addressLine2 ?? ''} placeholder="Apt, suite, unit, etc. (optional)" onChange={e => set('addressLine2', e.target.value)} />
                    </div>
                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">City <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.city ? ' input-error' : ''}`} value={form.city ?? ''} onChange={e => set('city', e.target.value)} />
                            {fieldErrors.city && <span className="field-error-msg">{fieldErrors.city}</span>}
                        </div>
                        <div className="profile-field">
                            <label className="form-label">State / Province <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.state ? ' input-error' : ''}`} value={form.state ?? ''} onChange={e => set('state', e.target.value)} />
                            {fieldErrors.state && <span className="field-error-msg">{fieldErrors.state}</span>}
                        </div>
                    </div>
                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">Postal Code <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.postalCode ? ' input-error' : ''}`} value={form.postalCode ?? ''} onChange={e => set('postalCode', e.target.value)} />
                            {fieldErrors.postalCode && <span className="field-error-msg">{fieldErrors.postalCode}</span>}
                        </div>
                        <div className="profile-field">
                            <label className="form-label">Country <span className="field-required">*</span></label>
                            <input className={`profile-input${fieldErrors.country ? ' input-error' : ''}`} value={form.country ?? ''} placeholder="US" onChange={e => set('country', e.target.value)} />
                            {fieldErrors.country && <span className="field-error-msg">{fieldErrors.country}</span>}
                        </div>
                    </div>

                    {error && <div className="error-msg" style={{ marginTop: '1rem' }}>{error}</div>}
                    {saved && <div className="success-msg" style={{ marginTop: '1rem' }}>Account details saved.</div>}

                    <div style={{ marginTop: '1.5rem' }}>
                        <button className="btn-primary-full" onClick={handleSave} disabled={saving}>
                            {saving ? 'Saving…' : 'Save Changes'}
                        </button>
                    </div>
                </div>
            </main>
            {showCashForm && defaultPortfolioId != null && (
                <CashForm portfolioId={defaultPortfolioId} onClose={() => setShowCashForm(false)} onSuccess={() => {}} />
            )}

            {updatePhoneStep !== null && (
                <div className="modal-overlay" onClick={() => setUpdatePhoneStep(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>{updatePhoneStep === 'enter-phone' ? 'Update Cell Phone Number' : 'Enter Verification Code'}</h2>
                            <button className="modal-close" onClick={() => setUpdatePhoneStep(null)}>✕</button>
                        </div>

                        {updatePhoneStep === 'enter-phone' && (
                            <>
                                <p style={{ color: 'var(--text-gray)', marginBottom: '1rem' }}>
                                    Enter your new cell phone number. A verification code will be sent to it via SMS.
                                </p>
                                <label className="form-label">New Cell Phone Number</label>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                                    <span style={{ padding: '0.5rem 0.75rem', background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 6, color: 'var(--text-gray)', fontWeight: 600 }}>+1</span>
                                    <input
                                        type="tel"
                                        value={updateNewPhone}
                                        onChange={e => setUpdateNewPhone(e.target.value)}
                                        placeholder="555-000-0000"
                                        autoFocus
                                        style={{ flex: 1, margin: 0 }}
                                    />
                                </div>
                                {updatePhoneError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{updatePhoneError}</div>}
                                <button className="btn-primary-full" onClick={handleSendUpdatePhoneCode} disabled={updatePhoneSending}>
                                    {updatePhoneSending ? 'Sending…' : 'Send Verification Code'}
                                </button>
                            </>
                        )}

                        {updatePhoneStep === 'enter-code' && (
                            <>
                                <p style={{ color: 'var(--text-gray)', marginBottom: '1rem' }}>
                                    A verification code was sent via SMS to <strong>{updateNewPhone}</strong>. Enter it below.
                                </p>
                                <label className="form-label">Verification Code</label>
                                <input
                                    type="text"
                                    value={updatePhoneCode}
                                    onChange={e => setUpdatePhoneCode(e.target.value)}
                                    placeholder="6-digit code"
                                    autoFocus
                                    style={{ marginBottom: '1rem' }}
                                />
                                {updatePhoneError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{updatePhoneError}</div>}
                                <div style={{ display: 'flex', gap: '0.75rem' }}>
                                    <button className="btn-primary-full" onClick={handleVerifyUpdatePhoneCode} disabled={updatePhoneSending}>
                                        {updatePhoneSending ? 'Verifying…' : 'Verify & Save'}
                                    </button>
                                    <button
                                        className="btn-primary-full"
                                        style={{ background: 'var(--card-bg)', border: '1px solid var(--border)' }}
                                        onClick={() => setUpdatePhoneStep('enter-phone')}
                                        disabled={updatePhoneSending}
                                    >
                                        Back
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}

            {updateEmailStep !== null && (
                <div className="modal-overlay" onClick={() => setUpdateEmailStep(null)}>
                    <div className="modal-card" onClick={e => e.stopPropagation()}>
                        <div className="modal-header">
                            <h2>{updateEmailStep === 'enter-email' ? 'Update Email Address' : 'Enter Verification Code'}</h2>
                            <button className="modal-close" onClick={() => setUpdateEmailStep(null)}>✕</button>
                        </div>

                        {updateEmailStep === 'enter-email' && (
                            <>
                                <p style={{ color: 'var(--text-gray)', marginBottom: '1rem' }}>
                                    Enter your new email address. A verification code will be sent to it.
                                </p>
                                <label className="form-label">New Email Address</label>
                                <input
                                    type="email"
                                    value={updateNewEmail}
                                    onChange={e => setUpdateNewEmail(e.target.value)}
                                    placeholder="new@email.com"
                                    autoFocus
                                    style={{ marginBottom: '1rem' }}
                                />
                                {updateEmailError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{updateEmailError}</div>}
                                <button className="btn-primary-full" onClick={handleSendUpdateCode} disabled={updateEmailSending}>
                                    {updateEmailSending ? 'Sending…' : 'Send Verification Code'}
                                </button>
                            </>
                        )}

                        {updateEmailStep === 'enter-code' && (
                            <>
                                <p style={{ color: 'var(--text-gray)', marginBottom: '1rem' }}>
                                    A verification code was sent to <strong>{updateNewEmail}</strong>. Enter it below.
                                </p>
                                <label className="form-label">Verification Code</label>
                                <input
                                    type="text"
                                    value={updateEmailCode}
                                    onChange={e => setUpdateEmailCode(e.target.value)}
                                    placeholder="6-digit code"
                                    autoFocus
                                    style={{ marginBottom: '1rem' }}
                                />
                                {updateEmailError && <div className="error-msg" style={{ marginBottom: '1rem' }}>{updateEmailError}</div>}
                                <div style={{ display: 'flex', gap: '0.75rem' }}>
                                    <button className="btn-primary-full" onClick={handleVerifyUpdateCode} disabled={updateEmailSending}>
                                        {updateEmailSending ? 'Verifying…' : 'Verify & Save'}
                                    </button>
                                    <button
                                        className="btn-primary-full"
                                        style={{ background: 'var(--card-bg)', border: '1px solid var(--border)' }}
                                        onClick={() => setUpdateEmailStep('enter-email')}
                                        disabled={updateEmailSending}
                                    >
                                        Back
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default AccountMaintenance;
