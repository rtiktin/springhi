import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getProfile, saveProfile } from '../api/profileApi';
import type { UserProfile } from '../api/profileApi';
import { getOrCreateDefaultPortfolio } from '../api/portfolioApi';
import CashForm from '../components/CashForm';
import { getLoggedInUsername, isAdmin } from '../utils/auth';

const RISK_LEVELS = ['conservative', 'moderate', 'moderate_aggressive', 'aggressive'];
const GOALS = ['income', 'balanced', 'growth', 'speculation'];
const LIQUIDITY = ['low', 'medium', 'high'];
const KNOWLEDGE = ['beginner', 'intermediate', 'advanced', 'expert'];

const emptyProfile: UserProfile = {
    riskLevel: 'moderate',
    goal: 'growth',
    horizonYears: 10,
    liquidityNeeds: 'low',
    knowledgeLevel: 'intermediate',
    additionalComments: '',
    availableCash: 0,
    currency: 'USD',
    sectorConstraints: [],
};

interface ProfileFieldErrors {
    horizonYears?: string;
}

function validateProfile(profile: UserProfile): ProfileFieldErrors {
    const errs: ProfileFieldErrors = {};
    const horizon = Number(profile.horizonYears);
    if (!profile.horizonYears && profile.horizonYears !== 0) {
        errs.horizonYears = 'Time horizon is required.';
    } else if (!Number.isInteger(horizon) || horizon < 1 || horizon > 50) {
        errs.horizonYears = 'Time horizon must be a whole number between 1 and 50.';
    }
    return errs;
}

const ProfileEdit: React.FC = () => {
    const navigate = useNavigate();
    const username = getLoggedInUsername();
    const [profile, setProfile] = useState<UserProfile>(emptyProfile);
    const [sectorInput, setSectorInput] = useState('');
    const [fieldErrors, setFieldErrors] = useState<ProfileFieldErrors>({});
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState('');
    const [showCashForm, setShowCashForm] = useState(false);
    const [defaultPortfolioId, setDefaultPortfolioId] = useState<number | null>(null);

    useEffect(() => {
        getProfile()
            .then(data => {
                if (data) {
                    setProfile(data);
                    setSectorInput(data.sectorConstraints.join(', '));
                }
            })
            .catch(() => {});
        getOrCreateDefaultPortfolio()
            .then(p => setDefaultPortfolioId(p.id))
            .catch(() => {});
    }, []);

    const handleChange = (field: keyof UserProfile, value: string | number | string[]) => {
        setProfile(prev => ({ ...prev, [field]: value }));
        if (field in fieldErrors) {
            setFieldErrors(prev => ({ ...prev, [field]: undefined }));
        }
    };

    const handleSave = async () => {
        const errs = validateProfile(profile);
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
            const sectors = sectorInput
                .split(',')
                .map(s => s.trim())
                .filter(s => s.length > 0);
            const updated = await saveProfile({ ...profile, sectorConstraints: sectors });
            setProfile(updated);
            setSectorInput(updated.sectorConstraints.join(', '));
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
        } catch {
            setError('Failed to save profile.');
        } finally {
            setSaving(false);
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        navigate('/login');
    };

    return (
        <div className="portfolio-page">
            {showCashForm && defaultPortfolioId != null && (
                <CashForm
                    portfolioId={defaultPortfolioId}
                    onClose={() => setShowCashForm(false)}
                    onSuccess={() => setShowCashForm(false)}
                />
            )}
            <header className="navbar">
                <div className="navbar-brand">
                    <Link to="/portfolio" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome">Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/portfolio" className="btn-logout">Portfolio</Link>
                    <Link to="/account" className="btn-logout">Account</Link>
                    {isAdmin() && <Link to="/admin" className="btn-logout">Admin</Link>}
                    <button className="btn-logout" onClick={() => setShowCashForm(true)}>$ Cash</button>
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <h1 className="portfolio-heading">Default Profile</h1>
                <p className="portfolio-sub">Your profile guides the AI portfolio optimizer.</p>

                <div className="profile-form-card">
                    <div className="profile-section-title">Risk &amp; Goals</div>

                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">Risk Tolerance</label>
                            <select
                                className="profile-select"
                                value={profile.riskLevel}
                                onChange={e => handleChange('riskLevel', e.target.value)}
                            >
                                {RISK_LEVELS.map(r => (
                                    <option key={r} value={r}>{r.replace('_', ' ')}</option>
                                ))}
                            </select>
                        </div>
                        <div className="profile-field">
                            <label className="form-label">Primary Goal</label>
                            <select
                                className="profile-select"
                                value={profile.goal}
                                onChange={e => handleChange('goal', e.target.value)}
                            >
                                {GOALS.map(g => (
                                    <option key={g} value={g}>{g}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">Time Horizon (years) <span className="field-required">*</span></label>
                            <input
                                type="number"
                                className={`profile-input${fieldErrors.horizonYears ? ' input-error' : ''}`}
                                value={profile.horizonYears}
                                min={1}
                                max={50}
                                onChange={e => handleChange('horizonYears', parseInt(e.target.value) || 1)}
                            />
                            {fieldErrors.horizonYears && <span className="field-error-msg">{fieldErrors.horizonYears}</span>}
                        </div>
                        <div className="profile-field">
                            <label className="form-label">Liquidity Needs</label>
                            <select
                                className="profile-select"
                                value={profile.liquidityNeeds}
                                onChange={e => handleChange('liquidityNeeds', e.target.value)}
                            >
                                {LIQUIDITY.map(l => (
                                    <option key={l} value={l}>{l}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="profile-row">
                        <div className="profile-field">
                            <label className="form-label">Knowledge Level</label>
                            <select
                                className="profile-select"
                                value={profile.knowledgeLevel}
                                onChange={e => handleChange('knowledgeLevel', e.target.value)}
                            >
                                {KNOWLEDGE.map(k => (
                                    <option key={k} value={k}>{k}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <div className="profile-field" style={{ marginTop: '0.5rem' }}>
                        <label className="form-label">Preferred Sectors (comma-separated)</label>
                        <input
                            type="text"
                            className="profile-input"
                            value={sectorInput}
                            placeholder="e.g. tech, healthcare, energy"
                            onChange={e => setSectorInput(e.target.value)}
                        />
                    </div>

                    <div className="profile-field" style={{ marginTop: '0.5rem' }}>
                        <label className="form-label">Additional Notes</label>
                        <textarea
                            className="profile-textarea"
                            value={profile.additionalComments}
                            rows={4}
                            placeholder="Any preferences, constraints, or notes for the AI optimizer..."
                            onChange={e => handleChange('additionalComments', e.target.value)}
                        />
                    </div>

                    {error && <div className="error-msg">{error}</div>}
                    {saving && <div className="info-msg" style={{ color: '#818cf8', fontWeight: 600 }}>Saving profile…</div>}
                    {saved && <div className="success-msg">Profile saved successfully.</div>}

                    <div style={{ marginTop: '1.5rem' }}>
                        <button
                            className="btn-primary-full"
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? 'Saving...' : 'Save Profile'}
                        </button>
                    </div>
                </div>
            </main>
        </div>
    );
};

export default ProfileEdit;
