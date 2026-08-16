import React, { useEffect, useState } from 'react';
import axios from 'axios';
import API_GATEWAY from '../api/apiBase';
import type { OptimizationSchedule } from '../api/portfolioApi';
import {
    getOptimizationSchedules,
    getAllOptimizationSchedules,
    createOptimizationSchedule,
    updateOptimizationSchedule,
    deleteOptimizationSchedule,
} from '../api/portfolioApi';

interface Props {
    portfolioId: number;
    onUpgradeRequired?: (message: string) => void;
}

const FREQUENCIES = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'] as const;
const DAY_NAMES = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const PROVIDERS = ['gemini', 'claude', 'chatgpt'] as const;

const fmtDt = (s: string | null) =>
    s ? new Date(s).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—';

const describeSchedule = (s: OptimizationSchedule): string => {
    switch (s.frequency) {
        case 'DAILY':   return 'Every weekday';
        case 'WEEKLY':  return `Every ${s.dayOfWeek ? DAY_NAMES[s.dayOfWeek] : 'Monday'}`;
        case 'MONTHLY': return `Day ${s.dayOfMonth ?? 1} of each month`;
        case 'QUARTERLY': return `Day ${s.dayOfMonth ?? 1} of each quarter`;
        case 'YEARLY':  return `Day ${s.dayOfMonth ?? 1} of each year`;
        default:        return s.frequency;
    }
};

const blank = { frequency: 'MONTHLY' as const, aiProvider: 'gemini', dayOfWeek: 1, dayOfMonth: 1 };

const MONTHLY_RUNS: Record<string, number> = {
    DAILY: 22,
    WEEKLY: 4.33,
    MONTHLY: 1,
    QUARTERLY: 0.33,
    YEARLY: 0.083,
};

const authHeader = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

const ScheduleManager: React.FC<Props> = ({ portfolioId, onUpgradeRequired }) => {
    const [schedules, setSchedules] = useState<OptimizationSchedule[]>([]);
    const [loading, setLoading] = useState(true);
    const [showForm, setShowForm] = useState(false);
    const [editing, setEditing] = useState<OptimizationSchedule | null>(null);
    const [form, setForm] = useState(blank);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [confirmDelete, setConfirmDelete] = useState<number | null>(null);
    const [isFree, setIsFree] = useState(false);
    const [quotaInfo, setQuotaInfo] = useState<{
        usedThisMonth: number;
        planMax: number;
        premiumMax: number;
        planName: string;
        isFreeLimit: boolean;
        existingScheduledMonthly: number;
    } | null>(null);
    const [quotaChecking, setQuotaChecking] = useState(false);

    const load = () => {
        setLoading(true);
        getOptimizationSchedules(portfolioId)
            .then(setSchedules)
            .catch(() => setError('Failed to load schedules.'))
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        load();
        axios.get(`${API_GATEWAY}/api/v1/subscription/limits`, { headers: authHeader() })
            .then(res => setIsFree((res.data.planName ?? 'FREE').toUpperCase() === 'FREE'))
            .catch(() => setIsFree(true));
    }, [portfolioId]);

    const loadQuota = async (editingScheduleId?: number): Promise<typeof quotaInfo> => {
        const [limitsRes, usageRes, allSchedules] = await Promise.all([
            axios.get(`${API_GATEWAY}/api/v1/subscription/limits`, { headers: authHeader() }),
            axios.get(`${API_GATEWAY}/api/v1/portfolios/usage-stats`, { headers: authHeader() }),
            getAllOptimizationSchedules(),
        ]);
        const planMax: number = limitsRes.data.maxOptimizationsPerMonth ?? 0;
        const premiumMax: number = limitsRes.data.premiumMaxOptimizationsPerMonth ?? planMax;
        const planName: string = limitsRes.data.planName ?? 'FREE';
        const isFreeLimit: boolean = usageRes.data.isFreeLimit ?? true;
        const usedThisMonth: number = usageRes.data.optimizationsThisMonth ?? 0;
        const existingScheduledMonthly = allSchedules
            .filter(s => s.enabled && s.id !== editingScheduleId)
            .reduce((sum, s) => sum + (MONTHLY_RUNS[s.frequency] ?? 0), 0);
        const info = { usedThisMonth, planMax, premiumMax, planName, isFreeLimit, existingScheduledMonthly };
        setQuotaInfo(info);
        return info;
    };

    const checkScheduleCapacity = (frequency: string, info: NonNullable<typeof quotaInfo>): 'ok' | 'upgrade' | 'exceeded' => {
        const newMonthly = MONTHLY_RUNS[frequency] ?? 1;
        const projected = info.usedThisMonth + info.existingScheduledMonthly + newMonthly;
        if (projected > info.premiumMax) return 'exceeded';
        if (projected > info.planMax) return 'upgrade';
        return 'ok';
    };

    const openNew = async () => {
        if (isFree) {
            if (onUpgradeRequired) {
                onUpgradeRequired('Scheduled optimizations are not available on the Free plan. Please upgrade to enable automatic portfolio re-optimization.');
            }
            return;
        }
        setQuotaChecking(true);
        try {
            const info = await loadQuota();
            if (info) {
                const worstCase = checkScheduleCapacity('MONTHLY', info);
                if (worstCase === 'exceeded') {
                    setError(`Adding even a monthly schedule would exceed the maximum allowed optimizations (${info.premiumMax}) on the Premium plan. This schedule cannot be created.`);
                    return;
                }
            }
        } catch {
        } finally {
            setQuotaChecking(false);
        }
        setEditing(null);
        setForm(blank);
        setError('');
        setShowForm(true);
    };

    const openEdit = (s: OptimizationSchedule) => {
        setEditing(s);
        setForm({
            frequency: s.frequency,
            aiProvider: s.aiProvider,
            dayOfWeek: s.dayOfWeek ?? 1,
            dayOfMonth: s.dayOfMonth ?? 1,
        });
        setError('');
        setShowForm(true);
    };

    const handleSave = async () => {
        setSaving(true);
        setError('');
        try {
            if (!editing) {
                const info = await loadQuota();
                if (info) {
                    const result = checkScheduleCapacity(form.frequency, info);
                    if (result === 'exceeded') {
                        setError(`This schedule would exceed the maximum allowed optimizations (${info.premiumMax}) even on the Premium plan. It cannot be created.`);
                        return;
                    }
                    if (result === 'upgrade') {
                        if (onUpgradeRequired) {
                            onUpgradeRequired(`This schedule would exceed your ${info.planName} plan's optimization limit (${info.planMax}/month). Please upgrade to add more schedules.`);
                        }
                        return;
                    }
                }
            }
            const body = {
                frequency: form.frequency,
                aiProvider: form.aiProvider,
                dayOfWeek:  form.frequency === 'WEEKLY' ? form.dayOfWeek : null,
                dayOfMonth: ['MONTHLY', 'QUARTERLY', 'YEARLY'].includes(form.frequency) ? form.dayOfMonth : null,
            };
            if (editing) {
                const updated = await updateOptimizationSchedule(editing.id, portfolioId, body);
                setSchedules(prev => prev.map(s => s.id === updated.id ? updated : s));
            } else {
                const created = await createOptimizationSchedule(portfolioId, body);
                setSchedules(prev => [...prev, created]);
            }
            setShowForm(false);
        } catch {
            setError('Failed to save schedule.');
        } finally {
            setSaving(false);
        }
    };

    const handleToggle = async (s: OptimizationSchedule) => {
        try {
            const updated = await updateOptimizationSchedule(s.id, portfolioId, { enabled: !s.enabled });
            setSchedules(prev => prev.map(x => x.id === updated.id ? updated : x));
        } catch {
            setError('Failed to update schedule.');
        }
    };

    const handleDelete = async (id: number) => {
        try {
            await deleteOptimizationSchedule(id, portfolioId);
            setSchedules(prev => prev.filter(s => s.id !== id));
        } catch {
            setError('Failed to delete schedule.');
        } finally {
            setConfirmDelete(null);
        }
    };

    const inputStyle: React.CSSProperties = {
        background: 'var(--bg-dark)', color: 'var(--text-primary)', border: '1px solid var(--border)',
        borderRadius: 6, padding: '0.45rem 0.7rem', fontSize: '0.9rem', width: '100%', boxSizing: 'border-box',
    };
    const labelStyle: React.CSSProperties = { fontSize: '0.8rem', color: 'var(--text-gray)', marginBottom: '0.25rem', display: 'block' };
    const btnPrimary: React.CSSProperties = {
        background: '#6c47ff', color: '#fff', border: 'none', borderRadius: 6,
        padding: '0.45rem 1.1rem', cursor: 'pointer', fontWeight: 600, fontSize: '0.9rem',
    };
    const btnSecondary: React.CSSProperties = {
        background: 'transparent', color: 'var(--text-gray)', border: '1px solid var(--border)',
        borderRadius: 6, padding: '0.45rem 1rem', cursor: 'pointer', fontSize: '0.9rem',
    };

    return (
        <div style={{ marginTop: '1.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
                <h3 style={{ margin: 0, fontWeight: 700, fontSize: '1rem', color: 'var(--text-primary)' }}>
                    Scheduled Optimizations
                </h3>
                <button style={{ ...btnPrimary, opacity: quotaChecking ? 0.6 : 1 }} onClick={openNew} disabled={quotaChecking}>
                    {quotaChecking ? 'Checking…' : '+ Add Schedule'}
                </button>
            </div>

            {error && <p style={{ color: '#ef4444', fontSize: '0.85rem', marginBottom: '0.5rem' }}>{error}</p>}

            {loading ? (
                <p style={{ color: 'var(--text-gray)', fontSize: '0.85rem' }}>Loading…</p>
            ) : schedules.length === 0 ? (
                <p style={{ color: 'var(--text-gray)', fontSize: '0.85rem' }}>
                    No schedules yet. Add one to have your portfolio automatically re-optimized with AI.
                </p>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {schedules.map(s => (
                        <div key={s.id} style={{
                            background: 'var(--bg-dark)', border: '1px solid var(--border)', borderRadius: 8,
                            padding: '0.75rem 1rem', display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap',
                            opacity: s.enabled ? 1 : 0.55,
                        }}>
                            <div style={{ flex: 1, minWidth: 180 }}>
                                <div style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                                    {describeSchedule(s)}
                                </div>
                                <div style={{ fontSize: '0.78rem', color: 'var(--text-gray)', marginTop: '0.15rem' }}>
                                    {s.aiProvider.charAt(0).toUpperCase() + s.aiProvider.slice(1)} &nbsp;·&nbsp;
                                    Next: {fmtDt(s.nextRunAt)} &nbsp;·&nbsp;
                                    Last: {fmtDt(s.lastRunAt)}
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                                <button
                                    onClick={() => handleToggle(s)}
                                    style={{ ...btnSecondary, fontSize: '0.78rem', padding: '0.3rem 0.7rem' }}
                                    title={s.enabled ? 'Pause schedule' : 'Enable schedule'}
                                >
                                    {s.enabled ? 'Pause' : 'Enable'}
                                </button>
                                <button onClick={() => openEdit(s)} style={{ ...btnSecondary, fontSize: '0.78rem', padding: '0.3rem 0.7rem' }}>
                                    Edit
                                </button>
                                {confirmDelete === s.id ? (
                                    <>
                                        <button onClick={() => handleDelete(s.id)} style={{ ...btnSecondary, fontSize: '0.78rem', padding: '0.3rem 0.7rem', color: '#ef4444', borderColor: '#ef4444' }}>
                                            Confirm Delete
                                        </button>
                                        <button onClick={() => setConfirmDelete(null)} style={{ ...btnSecondary, fontSize: '0.78rem', padding: '0.3rem 0.7rem' }}>
                                            Cancel
                                        </button>
                                    </>
                                ) : (
                                    <button onClick={() => setConfirmDelete(s.id)} style={{ ...btnSecondary, fontSize: '0.78rem', padding: '0.3rem 0.7rem', color: '#ef4444', borderColor: '#ef4444' }}>
                                        Delete
                                    </button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {showForm && (
                <div className="modal-overlay" onClick={() => { if (!saving) setShowForm(false); }}>
                    <div className="modal-card" onClick={e => e.stopPropagation()} style={{ maxWidth: 420 }}>
                        <div className="modal-header">
                            <h2 style={{ margin: 0 }}>{editing ? 'Edit Schedule' : 'New Optimization Schedule'}</h2>
                            <button className="modal-close" onClick={() => setShowForm(false)} disabled={saving}>✕</button>
                        </div>
                        <div style={{ padding: '1.25rem 1.5rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                            <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--text-gray)' }}>
                                Trades will be executed automatically at 10:30 AM ET on the scheduled day using current market prices.
                            </p>

                            <div>
                                <label style={labelStyle}>Frequency</label>
                                <select style={inputStyle} value={form.frequency}
                                    onChange={e => setForm(f => ({ ...f, frequency: e.target.value as typeof form.frequency }))}>
                                    {FREQUENCIES.map(f => <option key={f} value={f}>{f.charAt(0) + f.slice(1).toLowerCase()}</option>)}
                                </select>
                            </div>

                            {form.frequency === 'WEEKLY' && (
                                <div>
                                    <label style={labelStyle}>Day of Week</label>
                                    <select style={inputStyle} value={form.dayOfWeek ?? 1}
                                        onChange={e => setForm(f => ({ ...f, dayOfWeek: Number(e.target.value) }))}>
                                        {[1, 2, 3, 4, 5].map(d => <option key={d} value={d}>{DAY_NAMES[d]}</option>)}
                                    </select>
                                </div>
                            )}

                            {['MONTHLY', 'QUARTERLY', 'YEARLY'].includes(form.frequency) && (
                                <div>
                                    <label style={labelStyle}>Day of Month</label>
                                    <select style={inputStyle} value={form.dayOfMonth ?? 1}
                                        onChange={e => setForm(f => ({ ...f, dayOfMonth: Number(e.target.value) }))}>
                                        {Array.from({ length: 28 }, (_, i) => i + 1).map(d =>
                                            <option key={d} value={d}>{d}</option>)}
                                    </select>
                                </div>
                            )}

                            <div>
                                <label style={labelStyle}>AI Model</label>
                                <select style={inputStyle} value={form.aiProvider}
                                    onChange={e => setForm(f => ({ ...f, aiProvider: e.target.value }))}>
                                    {PROVIDERS.map(p => <option key={p} value={p}>{p.charAt(0).toUpperCase() + p.slice(1)}</option>)}
                                </select>
                            </div>

                            {!editing && quotaInfo && (() => {
                                const newMonthly = MONTHLY_RUNS[form.frequency] ?? 1;
                                const projected = quotaInfo.usedThisMonth + quotaInfo.existingScheduledMonthly + newMonthly;
                                const status = checkScheduleCapacity(form.frequency, quotaInfo);
                                return (
                                    <p style={{ margin: 0, fontSize: '0.82rem', color: status === 'ok' ? 'var(--text-gray)' : status === 'upgrade' ? '#f59e0b' : '#ef4444' }}>
                                        Projected monthly runs: ~{projected.toFixed(1)} / {quotaInfo.planMax} allowed
                                        {status === 'upgrade' && ' — upgrade required'}
                                        {status === 'exceeded' && ' — exceeds Premium limit'}
                                    </p>
                                );
                            })()}

                            {error && <p style={{ color: '#ef4444', fontSize: '0.85rem', margin: 0 }}>{error}</p>}

                            <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'flex-end' }}>
                                <button style={btnSecondary} onClick={() => setShowForm(false)} disabled={saving}>Cancel</button>
                                <button style={btnPrimary} onClick={handleSave} disabled={saving}>
                                    {saving ? 'Saving…' : editing ? 'Save Changes' : 'Create Schedule'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ScheduleManager;
