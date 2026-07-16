import React, { useEffect, useState } from 'react';
import { getPortfolioProfile, savePortfolioProfile } from '../api/portfolioApi';
import type { PortfolioProfile } from '../api/portfolioApi';

const RISK_LEVELS = ['conservative', 'moderate', 'moderate_aggressive', 'aggressive'];
const GOALS = ['income', 'balanced', 'growth', 'speculation'];
const LIQUIDITY = ['low', 'medium', 'high'];

interface Props {
    portfolioId: number;
    bannerMessage?: string;
    onSaveSuccess?: () => void;
}

const NA = '__NA__';

const PortfolioProfileForm: React.FC<Props> = ({ portfolioId, bannerMessage, onSaveSuccess }) => {
    const [profile, setProfile] = useState<PortfolioProfile | null>(null);
    const [sectorInput, setSectorInput] = useState('');
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);
    const [error, setError] = useState('');
    const [horizonStr, setHorizonStr] = useState('');

    useEffect(() => {
        setProfile(null);
        setError('');
        setSaved(false);
        getPortfolioProfile(portfolioId)
            .then(p => {
                setProfile(p);
                setSectorInput((p.sectorConstraints ?? []).join(', '));
                setHorizonStr(p.horizonYears != null ? String(p.horizonYears) : '');
            })
            .catch(() => setError('Failed to load portfolio profile.'));
    }, [portfolioId]);

    const hasNotes = (profile?.additionalComments ?? '').trim().length > 0;

    const set = <K extends keyof PortfolioProfile>(field: K, value: PortfolioProfile[K]) =>
        setProfile(prev => prev ? { ...prev, [field]: value } : prev);

    const handleSave = async () => {
        if (!profile) return;
        if (!hasNotes) {
            if (!profile.riskLevel) { setError('Risk Tolerance is required unless Additional Notes are provided.'); return; }
            if (!profile.goal) { setError('Primary Goal is required unless Additional Notes are provided.'); return; }
            if (profile.horizonYears == null || profile.horizonYears < 1) { setError('Time Horizon is required (1–50 years) unless Additional Notes are provided.'); return; }
            if (!profile.liquidityNeeds) { setError('Liquidity Needs is required unless Additional Notes are provided.'); return; }
        }
        setSaving(true);
        setError('');
        setSaved(false);
        try {
            const sectors = sectorInput.split(',').map(s => s.trim()).filter(s => s.length > 0);
            const updated = await savePortfolioProfile(portfolioId, { ...profile, sectorConstraints: sectors });
            setProfile(updated);
            setSectorInput((updated.sectorConstraints ?? []).join(', '));
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
            onSaveSuccess?.();

        } catch {
            setError('Failed to save portfolio profile.');
        } finally {
            setSaving(false);
        }
    };

    if (!profile) return <div className="portfolio-loading">{error || 'Loading profile…'}</div>;

    return (
        <div style={{ maxWidth: 640, margin: '0 auto', padding: '1.5rem 0' }}>
            <h2 style={{ marginBottom: '0.25rem', color: 'var(--text-light)' }}>Portfolio Investment Profile</h2>
            <p style={{ color: 'var(--text-gray)', fontSize: '0.88rem', marginBottom: '1.5rem' }}>
                These settings override your global Default Profile for this portfolio's AI optimization.
                Set Risk Tolerance, Goal, Time Horizon, and Liquidity Needs to N/A only when Additional Notes fully describe the strategy.
            </p>

            {bannerMessage && (
                <div style={{ background: '#2d2a00', border: '1px solid #facc15', color: '#facc15', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '1rem', fontWeight: 600 }}>
                    {bannerMessage}
                </div>
            )}
            {error && <div className="error-msg" style={{ marginBottom: '1rem' }}>{error}</div>}
            {saving && <div style={{ color: '#818cf8', marginBottom: '1rem', fontWeight: 600 }}>Saving profile…</div>}
            {saved && <div style={{ color: '#22c55e', marginBottom: '1rem', fontWeight: 600 }}>Profile saved.</div>}

            <label className="form-label">Risk Tolerance</label>
            <select
                value={profile.riskLevel ?? NA}
                onChange={e => set('riskLevel', e.target.value === NA ? null : e.target.value)}
                style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
            >
                {hasNotes && <option value={NA}>N/A — see Additional Notes</option>}
                {RISK_LEVELS.map(r => <option key={r} value={r}>{r.replace('_', ' ')}</option>)}
                {!hasNotes && !profile.riskLevel && <option value={NA} disabled>Select…</option>}
            </select>

            <label className="form-label">Primary Goal</label>
            <select
                value={profile.goal ?? NA}
                onChange={e => set('goal', e.target.value === NA ? null : e.target.value)}
                style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
            >
                {hasNotes && <option value={NA}>N/A — see Additional Notes</option>}
                {GOALS.map(g => <option key={g} value={g}>{g}</option>)}
                {!hasNotes && !profile.goal && <option value={NA} disabled>Select…</option>}
            </select>

            <label className="form-label">Time Horizon (years)</label>
            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '1rem' }}>
                <input
                    type="number"
                    min={1}
                    max={50}
                    placeholder={hasNotes ? 'N/A' : '1–50'}
                    value={horizonStr}
                    disabled={hasNotes && horizonStr === ''}
                    onChange={e => {
                        setHorizonStr(e.target.value);
                        const n = parseInt(e.target.value);
                        set('horizonYears', isNaN(n) ? null : n);
                    }}
                    style={{ flex: 1 }}
                />
                {hasNotes && (
                    <label style={{ color: 'var(--text-gray)', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                        <input
                            type="checkbox"
                            checked={profile.horizonYears == null}
                            onChange={e => {
                                if (e.target.checked) { set('horizonYears', null); setHorizonStr(''); }
                            }}
                            style={{ marginRight: '0.4rem' }}
                        />
                        N/A
                    </label>
                )}
            </div>

            <label className="form-label">Liquidity Needs</label>
            <select
                value={profile.liquidityNeeds ?? NA}
                onChange={e => set('liquidityNeeds', e.target.value === NA ? null : e.target.value)}
                style={{ background: '#1e2030', color: '#e2e8f0', width: '100%', marginBottom: '1rem' }}
            >
                {hasNotes && <option value={NA}>N/A — see Additional Notes</option>}
                {LIQUIDITY.map(l => <option key={l} value={l}>{l}</option>)}
                {!hasNotes && !profile.liquidityNeeds && <option value={NA} disabled>Select…</option>}
            </select>

            <label className="form-label">Preferred Sectors (comma-separated, optional)</label>
            <input
                type="text"
                placeholder="e.g. Technology, Healthcare"
                value={sectorInput}
                onChange={e => setSectorInput(e.target.value)}
                style={{ marginBottom: '1rem' }}
            />

            <label className="form-label">Additional Notes (optional)</label>
            <textarea
                placeholder="Describe any specific investment strategy, constraints, or goals for this portfolio…"
                value={profile.additionalComments ?? ''}
                onChange={e => set('additionalComments', e.target.value || null)}
                rows={5}
                style={{ resize: 'vertical', width: '100%', boxSizing: 'border-box', marginBottom: '1.5rem' }}
            />

            <button
                className="btn-primary-full"
                onClick={handleSave}
                disabled={saving}
            >
                {saving ? 'Saving…' : 'Save Portfolio Profile'}
            </button>
        </div>
    );
};

export default PortfolioProfileForm;
