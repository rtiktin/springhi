import React, { useEffect, useState, useCallback } from 'react';
import {
    listAds, createAd, deleteAd, getAdDetail, upsertAdStat, deleteAdStat,
    type AdComparisonRow, type AdDetail, type AdPayload, type AdStatPayload,
} from '../api/adsApi';

const PLATFORMS = ['TIKTOK', 'GOOGLE', 'META', 'YOUTUBE', 'LINKEDIN', 'X', 'EMAIL', 'OTHER'];

const inputStyle: React.CSSProperties = {
    background: 'var(--bg-dark)', color: 'var(--text-primary)',
    border: '1px solid var(--border)', borderRadius: 6, padding: '0.35rem 0.65rem', fontSize: '0.85rem',
};
const btnTrade: React.CSSProperties = { cursor: 'pointer' };
const thStyle: React.CSSProperties = { padding: '0.5rem', textAlign: 'left', color: 'var(--text-gray)', fontSize: '0.78rem', textTransform: 'uppercase', whiteSpace: 'nowrap' };
const tdStyle: React.CSSProperties = { padding: '0.5rem', whiteSpace: 'nowrap' };

const fmtMoney = (n: number) => `$${Number(n || 0).toFixed(2)}`;
const fmtPct = (n: number) => `${(Number(n || 0) * 100).toFixed(2)}%`;
const fmtRatio = (n: number) => `${Number(n || 0).toFixed(2)}x`;
const fmtNum = (n: number) => Number(n || 0).toLocaleString();

const todayStr = () => new Date().toISOString().slice(0, 10);
const daysAgoStr = (d: number) => { const x = new Date(); x.setDate(x.getDate() - d); return x.toISOString().slice(0, 10); };

const emptyStatForm = (): AdStatPayload => ({
    statDate: todayStr(), impressions: 0, clicks: 0, spend: 0, conversions: 0, revenue: 0,
});

const AdsPanel: React.FC = () => {
    const [from, setFrom] = useState(daysAgoStr(29));
    const [to, setTo] = useState(todayStr());
    const [rows, setRows] = useState<AdComparisonRow[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [msg, setMsg] = useState<{ text: string; error: boolean } | null>(null);

    const [showCreate, setShowCreate] = useState(false);
    const [createForm, setCreateForm] = useState<AdPayload>({ name: '', platform: 'TIKTOK', externalRef: '', status: 'ACTIVE', notes: '' });

    const [detail, setDetail] = useState<AdDetail | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [statForm, setStatForm] = useState<AdStatPayload>(emptyStatForm());
    const [statSaving, setStatSaving] = useState(false);

    const loadList = useCallback(() => {
        setLoading(true);
        setError('');
        listAds(from, to)
            .then(setRows)
            .catch(() => setError('Failed to load ads.'))
            .finally(() => setLoading(false));
    }, [from, to]);

    useEffect(() => { loadList(); }, [loadList]);

    const loadDetail = (id: number) => {
        setDetailLoading(true);
        setMsg(null);
        getAdDetail(id, from, to)
            .then(setDetail)
            .catch(() => setMsg({ text: 'Failed to load ad detail.', error: true }))
            .finally(() => setDetailLoading(false));
    };

    const submitCreate = (e: React.FormEvent) => {
        e.preventDefault();
        setMsg(null);
        const payload: AdPayload = {
            name: createForm.name.trim(),
            platform: createForm.platform,
            externalRef: createForm.externalRef?.trim() || null,
            status: createForm.status,
            notes: createForm.notes?.trim() || null,
        };
        createAd(payload)
            .then(() => { setShowCreate(false); setCreateForm({ name: '', platform: 'TIKTOK', externalRef: '', status: 'ACTIVE', notes: '' }); setMsg({ text: 'Ad created.', error: false }); loadList(); })
            .catch((err: any) => setMsg({ text: err.response?.data?.message || 'Failed to create ad.', error: true }));
    };

    const submitStat = (e: React.FormEvent) => {
        e.preventDefault();
        if (!detail) return;
        setStatSaving(true);
        setMsg(null);
        upsertAdStat(detail.id, statForm)
            .then(() => { setStatForm(emptyStatForm()); setMsg({ text: 'Stats saved.', error: false }); loadDetail(detail.id); loadList(); })
            .catch((err: any) => setMsg({ text: err.response?.data?.message || 'Failed to save stats.', error: true }))
            .finally(() => setStatSaving(false));
    };

    const editDay = (d: AdDetail['daily'][number]) => {
        setStatForm({
            statDate: d.statDate, impressions: d.impressions, clicks: d.clicks,
            spend: d.spend, conversions: d.conversions, revenue: d.revenue,
        });
    };

    const removeDay = (date: string) => {
        if (!detail) return;
        if (!confirm(`Delete stats for ${date}?`)) return;
        deleteAdStat(detail.id, date)
            .then(() => { setMsg({ text: 'Day deleted.', error: false }); loadDetail(detail.id); loadList(); })
            .catch(() => setMsg({ text: 'Failed to delete day.', error: true }));
    };

    const removeAd = () => {
        if (!detail) return;
        if (!confirm(`Delete ad "${detail.name}" and all its stats?`)) return;
        deleteAd(detail.id)
            .then(() => { setDetail(null); setMsg({ text: 'Ad deleted.', error: false }); loadList(); })
            .catch(() => setMsg({ text: 'Failed to delete ad.', error: true }));
    };

    const setRange = (days: number) => { setFrom(daysAgoStr(days - 1)); setTo(todayStr()); };

    if (detail) {
        return (
            <div>
                <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap' }}>
                    <button className="btn-logout" onClick={() => { setDetail(null); setStatForm(emptyStatForm()); }}>&larr; Back</button>
                    <h2 style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>{detail.name}</h2>
                    <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: 'var(--bg-dark)', color: 'var(--text-gray)' }}>{detail.platform}</span>
                    <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: detail.status === 'ACTIVE' ? '#1f3d2b' : '#3d2b1f', color: detail.status === 'ACTIVE' ? '#22c55e' : '#f59e0b' }}>{detail.status}</span>
                    <button className="btn-logout" style={{ marginLeft: 'auto', color: '#ef4444' }} onClick={removeAd}>Delete Ad</button>
                </div>

                {msg && <div style={{ background: msg.error ? '#fee2e2' : '#d1fae5', color: msg.error ? '#b91c1c' : '#065f46', borderRadius: 8, padding: '0.6rem 0.9rem', marginBottom: '1rem', fontSize: '0.9rem' }}>{msg.text}</div>}

                <div style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '0.6rem 0.8rem', marginBottom: '1rem', fontSize: '0.85rem', color: 'var(--text-gray)' }}>
                    Tracking link (share this — signups/revenue are auto-attributed):&nbsp;
                    <code style={{ color: 'var(--text-primary)' }}>{window.location.origin}/?ad={detail.trackingCode}</code>
                </div>

                {detailLoading ? <div className="portfolio-loading">Loading…</div> : (
                    <>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '0.6rem', marginBottom: '1.5rem' }}>
                            {[
                                ['Impressions', fmtNum(detail.impressions)],
                                ['Clicks', fmtNum(detail.clicks)],
                                ['Spend', fmtMoney(detail.spend)],
                                ['CTR', fmtPct(detail.ctr)],
                                ['CPC', fmtMoney(detail.cpc)],
                                ['Conversions', fmtNum(detail.conversions)],
                                ['Revenue', fmtMoney(detail.revenue)],
                                ['ROAS', fmtRatio(detail.roas)],
                                ['Signups', fmtNum(detail.autoSignups)],
                                ['Paid', fmtNum(detail.autoPaid)],
                                ['Attr Rev', fmtMoney(detail.autoRevenue)],
                                ['Attr ROAS', fmtRatio(detail.autoRoas)],
                            ].map(([l, v]) => (
                                <div key={l} style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '0.7rem 0.9rem' }}>
                                    <div style={{ fontSize: '0.7rem', color: 'var(--text-gray)', textTransform: 'uppercase' }}>{l}</div>
                                    <div style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--text-primary)' }}>{v}</div>
                                </div>
                            ))}
                        </div>

                        <h3 style={{ margin: '0 0 0.75rem', color: 'var(--text-primary)', fontSize: '1rem' }}>Add / update a day</h3>
                        <form onSubmit={submitStat} style={{ display: 'grid', gap: '0.5rem', marginBottom: '1.5rem' }}>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))', gap: '0.5rem' }}>
                                <input type="date" value={statForm.statDate} onChange={e => setStatForm({ ...statForm, statDate: e.target.value })} style={inputStyle} required />
                                <input type="number" min={0} placeholder="Impressions" value={statForm.impressions || ''} onChange={e => setStatForm({ ...statForm, impressions: +e.target.value })} style={inputStyle} />
                                <input type="number" min={0} placeholder="Clicks" value={statForm.clicks || ''} onChange={e => setStatForm({ ...statForm, clicks: +e.target.value })} style={inputStyle} />
                                <input type="number" min={0} step="0.01" placeholder="Spend" value={statForm.spend || ''} onChange={e => setStatForm({ ...statForm, spend: +e.target.value })} style={inputStyle} />
                                <input type="number" min={0} placeholder="Conversions" value={statForm.conversions || ''} onChange={e => setStatForm({ ...statForm, conversions: +e.target.value })} style={inputStyle} />
                                <input type="number" min={0} step="0.01" placeholder="Revenue" value={statForm.revenue || ''} onChange={e => setStatForm({ ...statForm, revenue: +e.target.value })} style={inputStyle} />
                            </div>
                            <div><button className="btn-trade" style={btnTrade} disabled={statSaving}>{statSaving ? 'Saving…' : 'Save Day'}</button></div>
                        </form>

                        <h3 style={{ margin: '0 0 0.75rem', color: 'var(--text-primary)', fontSize: '1rem' }}>Daily breakdown ({detail.rangeFrom} → {detail.rangeTo})</h3>
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                                <thead>
                                    <tr style={{ textAlign: 'left', color: 'var(--text-gray)', borderBottom: '1px solid var(--border)' }}>
                                        <th style={thStyle}>Date</th><th style={thStyle}>Impr.</th><th style={thStyle}>Clicks</th><th style={thStyle}>Spend</th>
                                        <th style={thStyle}>CTR</th><th style={thStyle}>CPC</th><th style={thStyle}>Conv.</th><th style={thStyle}>Revenue</th><th style={thStyle}>ROAS</th><th style={thStyle}></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {detail.daily.length === 0 ? (
                                        <tr><td colSpan={10} style={{ padding: '0.75rem', color: 'var(--text-gray)' }}>No stats in this range.</td></tr>
                                    ) : detail.daily.map(d => (
                                        <tr key={d.statDate} style={{ borderBottom: '1px solid var(--border)' }}>
                                            <td style={tdStyle}>{d.statDate}</td>
                                            <td style={tdStyle}>{fmtNum(d.impressions)}</td>
                                            <td style={tdStyle}>{fmtNum(d.clicks)}</td>
                                            <td style={tdStyle}>{fmtMoney(d.spend)}</td>
                                            <td style={tdStyle}>{fmtPct(d.ctr)}</td>
                                            <td style={tdStyle}>{fmtMoney(d.cpc)}</td>
                                            <td style={tdStyle}>{fmtNum(d.conversions)}</td>
                                            <td style={tdStyle}>{fmtMoney(d.revenue)}</td>
                                            <td style={tdStyle}>{fmtRatio(d.roas)}</td>
                                            <td style={tdStyle}>
                                                <button className="btn-logout" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', marginRight: '0.25rem' }} onClick={() => editDay(d)}>Edit</button>
                                                <button className="btn-logout" style={{ padding: '0.2rem 0.5rem', fontSize: '0.75rem', color: '#ef4444' }} onClick={() => removeDay(d.statDate)}>Del</button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}
</div>
        );
    }

    return (
        <div>
            <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
                <h2 style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>Ad Performance</h2>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-gray)' }}>From <input type="date" value={from} onChange={e => setFrom(e.target.value)} style={inputStyle} /></label>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-gray)' }}>To <input type="date" value={to} onChange={e => setTo(e.target.value)} style={inputStyle} /></label>
                <button className="btn-logout" onClick={() => setRange(7)}>7d</button>
                <button className="btn-logout" onClick={() => setRange(30)}>30d</button>
                <button className="btn-logout" onClick={() => setRange(90)}>90d</button>
                <button className="btn-logout" onClick={loadList}>Apply</button>
                <button className="btn-trade" style={btnTrade} onClick={() => { setShowCreate(s => !s); setMsg(null); }}>New Ad</button>
            </div>

            {msg && <div style={{ background: msg.error ? '#fee2e2' : '#d1fae5', color: msg.error ? '#b91c1c' : '#065f46', borderRadius: 8, padding: '0.6rem 0.9rem', marginBottom: '1rem', fontSize: '0.9rem' }}>{msg.text}</div>}
            {error && <div style={{ background: '#fee2e2', color: '#b91c1c', borderRadius: 8, padding: '0.6rem 0.9rem', marginBottom: '1rem', fontSize: '0.9rem' }}>{error}</div>}

            {showCreate && (
                <form onSubmit={submitCreate} style={{ background: 'var(--bg-dark)', borderRadius: 8, padding: '1rem', marginBottom: '1.5rem', display: 'grid', gap: '0.6rem' }}>
                    <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: '0.6rem' }}>
                        <input placeholder="Ad / video name" value={createForm.name} onChange={e => setCreateForm({ ...createForm, name: e.target.value })} style={inputStyle} required />
                        <select value={createForm.platform} onChange={e => setCreateForm({ ...createForm, platform: e.target.value })} style={inputStyle}>
                            {PLATFORMS.map(p => <option key={p} value={p}>{p}</option>)}
                        </select>
                        <select value={createForm.status} onChange={e => setCreateForm({ ...createForm, status: e.target.value })} style={inputStyle}>
                            <option value="ACTIVE">Active</option><option value="PAUSED">Paused</option><option value="ARCHIVED">Archived</option>
                        </select>
                    </div>
                    <input placeholder="External ref (optional)" value={createForm.externalRef ?? ''} onChange={e => setCreateForm({ ...createForm, externalRef: e.target.value })} style={inputStyle} />
                    <input placeholder="Notes (optional)" value={createForm.notes ?? ''} onChange={e => setCreateForm({ ...createForm, notes: e.target.value })} style={inputStyle} />
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <button className="btn-trade" style={btnTrade} type="submit">Create</button>
                        <button className="btn-logout" type="button" onClick={() => setShowCreate(false)}>Cancel</button>
                    </div>
                </form>
            )}

            {loading ? <div className="portfolio-loading">Loading ads…</div> : (
                <div style={{ overflowX: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                        <thead>
                            <tr style={{ textAlign: 'left', color: 'var(--text-gray)', borderBottom: '1px solid var(--border)' }}>
                                <th style={thStyle}>Name</th><th style={thStyle}>Platform</th><th style={thStyle}>Status</th>
                                <th style={thStyle}>Impr.</th><th style={thStyle}>Clicks</th><th style={thStyle}>Spend</th>
                                <th style={thStyle}>CTR</th><th style={thStyle}>CPC</th><th style={thStyle}>Conv.</th>
                                <th style={thStyle}>Revenue</th><th style={thStyle}>ROAS</th><th style={thStyle}>ROI</th>
                                <th style={thStyle}>Signups</th><th style={thStyle}>Paid</th><th style={thStyle}>Attr Rev</th><th style={thStyle}>Attr ROAS</th>
                            </tr>
                        </thead>
                        <tbody>
                            {rows.length === 0 ? (
                                <tr><td colSpan={16} style={{ padding: '0.75rem', color: 'var(--text-gray)' }}>No ads yet. Click “New Ad” to create one.</td></tr>
                            ) : rows.map(r => (
                                <tr key={r.id} style={{ borderBottom: '1px solid var(--border)', cursor: 'pointer' }} onClick={() => loadDetail(r.id)}>
                                    <td style={tdStyle}>{r.name}</td>
                                    <td style={tdStyle}>{r.platform}</td>
                                    <td style={tdStyle}><span style={{ fontSize: '0.7rem', padding: '0.15rem 0.4rem', borderRadius: 5, background: r.status === 'ACTIVE' ? '#1f3d2b' : '#3d2b1f', color: r.status === 'ACTIVE' ? '#22c55e' : '#f59e0b' }}>{r.status}</span></td>
                                    <td style={tdStyle}>{fmtNum(r.impressions)}</td>
                                    <td style={tdStyle}>{fmtNum(r.clicks)}</td>
                                    <td style={tdStyle}>{fmtMoney(r.spend)}</td>
                                    <td style={tdStyle}>{fmtPct(r.ctr)}</td>
                                    <td style={tdStyle}>{fmtMoney(r.cpc)}</td>
                                    <td style={tdStyle}>{fmtNum(r.conversions)}</td>
                                    <td style={tdStyle}>{fmtMoney(r.revenue)}</td>
                                    <td style={tdStyle}>{fmtRatio(r.roas)}</td>
                                    <td style={tdStyle}>{fmtPct(r.roi)}</td>
                                    <td style={tdStyle}>{fmtNum(r.autoSignups)}</td>
                                    <td style={tdStyle}>{fmtNum(r.autoPaid)}</td>
                                    <td style={tdStyle}>{fmtMoney(r.autoRevenue)}</td>
                                    <td style={tdStyle}>{fmtRatio(r.autoRoas)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
};

export default AdsPanel;
