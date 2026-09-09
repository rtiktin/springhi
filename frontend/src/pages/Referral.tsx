import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getLoggedInUsername, isAdmin } from '../utils/auth';
import ImpersonationBanner from '../components/ImpersonationBanner';
import { getMyReferral, getPayoutProfile, savePayoutProfile } from '../api/referralApi';
import type { ReferralDashboard, PayoutProfile, PayoutProfilePayload } from '../api/referralApi';

const StatCard: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
    <div className="profile-form-card" style={{ padding: '1rem 1.25rem' }}>
        <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.7 }}>{label}</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem' }}>{value}</div>
    </div>
);

const inputStyle: React.CSSProperties = { padding: '0.5rem 0.75rem', borderRadius: 8, border: '1px solid #3a3a3c', background: '#161618', color: '#fff' };

const Referral: React.FC = () => {
    const username = getLoggedInUsername();
    const navigate = useNavigate();
    const [data, setData] = useState<ReferralDashboard | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [copied, setCopied] = useState(false);
    const [profile, setProfile] = useState<PayoutProfile | null>(null);
    const [profileForm, setProfileForm] = useState<PayoutProfilePayload>({});
    const [taxIdInput, setTaxIdInput] = useState('');
    const [profileSaving, setProfileSaving] = useState(false);
    const [profileMsg, setProfileMsg] = useState<{ text: string; error: boolean } | null>(null);

    useEffect(() => {
        getMyReferral()
            .then(setData)
            .catch(() => setError('Failed to load referral dashboard.'))
            .finally(() => setLoading(false));
        getPayoutProfile()
            .then(p => {
                setProfile(p);
                setProfileForm({
                    payableName: p.payableName,
                    payoutEmail: p.payoutEmail,
                    international: p.international,
                    entityType: p.entityType,
                    addressLine1: p.addressLine1,
                    addressLine2: p.addressLine2,
                    city: p.city,
                    state: p.state,
                    postalCode: p.postalCode,
                    country: p.country,
                });
            })
            .catch(() => { /* payout profile optional until provided */ });
    }, []);

    const handleProfileChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { name, value, type } = e.target;
        const isCheckbox = type === 'checkbox';
        const checked = (e.target as HTMLInputElement).checked;
        setProfileForm(prev => ({ ...prev, [name]: isCheckbox ? checked : value }));
    };

    const saveProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        setProfileSaving(true);
        setProfileMsg(null);
        try {
            const payload: PayoutProfilePayload = { ...profileForm, taxId: taxIdInput || undefined };
            const saved = await savePayoutProfile(payload);
            setProfile(saved);
            setProfileForm({
                payableName: saved.payableName,
                payoutEmail: saved.payoutEmail,
                international: saved.international,
                entityType: saved.entityType,
                addressLine1: saved.addressLine1,
                addressLine2: saved.addressLine2,
                city: saved.city,
                state: saved.state,
                postalCode: saved.postalCode,
                country: saved.country,
            });
            setTaxIdInput('');
            setProfileMsg({ text: 'Payout details saved.', error: false });
        } catch (err: any) {
            const msg: string = err.response?.data?.message || 'Failed to save payout details.';
            setProfileMsg({ text: msg, error: true });
        } finally {
            setProfileSaving(false);
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('adminToken');
        navigate('/login');
    };

    const copyLink = async () => {
        if (!data) return;
        try {
            await navigator.clipboard.writeText(data.link);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {
            // ignore clipboard errors
        }
    };

    if (loading) return <div className="portfolio-loading">Loading…</div>;

    return (
        <div className="portfolio-page">
            <ImpersonationBanner />
            <header className="navbar">
                <div className="navbar-brand" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome" style={{ fontSize: '0.75rem', marginTop: '-0.2rem', opacity: 0.8 }}>Welcome back, {username}</span>}
                </div>
                <nav className="portfolio-nav">
                    <Link to="/getting-started" className="btn-logout">Getting Started</Link>
                    <Link to="/portfolio" className="btn-logout">Portfolios</Link>
                    <Link to="/leaderboard" className="btn-logout">Leaderboard</Link>
                    <Link to="/account" className="btn-logout">Account</Link>
                    <Link to="/subscription" className="btn-logout">Subscription</Link>
                    <Link to="/referral" className="btn-logout">Referral</Link>
                    {isAdmin() && <Link to="/admin" className="btn-logout">Admin</Link>}
                    <button className="btn-logout" onClick={handleLogout}>Log Out</button>
                </nav>
            </header>

            <main className="portfolio-main">
                <h1 className="portfolio-heading">Referral Program</h1>
                <p className="portfolio-sub">Earn 30% of every payment from your referrals for their first 12 months.</p>

                {error && <div className="error-msg">{error}</div>}

                {data && (
                    <>
                        <div className="profile-form-card" style={{ marginBottom: '1.5rem' }}>
                            <div className="profile-section-title">Your Referral Link</div>
                            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                                <input
                                    readOnly
                                    value={data.link}
                                    onClick={(e) => (e.target as HTMLInputElement).select()}
                                    style={{ flex: 1, minWidth: 240, padding: '0.5rem 0.75rem', borderRadius: 8, border: '1px solid #3a3a3c', background: '#161618', color: '#fff' }}
                                />
                                <button className="btn-trade" onClick={copyLink}>{copied ? 'Copied!' : 'Copy'}</button>
                            </div>
                            <p style={{ marginTop: '0.5rem', fontSize: '0.8rem', opacity: 0.7 }}>
                                Code: <strong>{data.code}</strong> · {data.active ? 'Active' : 'Inactive'}
                            </p>
                        </div>

                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
                            <StatCard label="Total Clicks" value={data.clicks} />
                            <StatCard label="Unique Clicks" value={data.uniqueClicks} />
                            <StatCard label="Signups" value={data.signups} />
                            <StatCard label="Conversions" value={data.conversions} />
                            <StatCard label="Pending (in hold)" value={`$${Number(data.pendingBalance).toFixed(2)}`} />
                            <StatCard label="Payable Balance" value={`$${Number(data.accruedBalance).toFixed(2)}`} />
                            <StatCard label="Paid Out" value={`$${Number(data.paidOut).toFixed(2)}`} />
                            <StatCard label="Clawed Back" value={`$${Number(data.clawedBack).toFixed(2)}`} />
                        </div>

                        <div className="profile-form-card" style={{ marginBottom: '1.5rem' }}>
                            <div className="profile-section-title">Payout Status</div>
                            {(() => {
                                const qualifierMet = data.liveReferred >= data.minLiveReferred;
                                const thresholdMet = Number(data.accruedBalance) >= data.payoutThreshold;
                                const methodLabel =
                                    data.payoutMethod === 'CONNECT' ? 'Stripe Connect — direct deposit'
                                    : data.payoutMethod === 'CSV' ? 'CSV payout — manual transfer'
                                    : 'Not yet determined';
                                const countryLabel = data.declaredCountry ? data.declaredCountry.toUpperCase() : 'Not declared';
                                return (
                                    <>
                                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '0.75rem', marginBottom: '0.75rem' }}>
                                            <div style={{ padding: '0.6rem 0.8rem', borderRadius: 8, background: '#161618', border: '1px solid #3a3a3c' }}>
                                                <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.7 }}>Payout method</div>
                                                <div style={{ fontWeight: 700, marginTop: '0.2rem' }}>{methodLabel}</div>
                                                <div style={{ fontSize: '0.75rem', opacity: 0.7, marginTop: '0.15rem' }}>
                                                    {data.payoutMethod === null
                                                        ? 'Declare your country below so we can choose your payout rail.'
                                                        : data.connectEnabled
                                                            ? (data.connectEligible ? 'Your country qualifies for direct Connect payouts.' : 'Your country uses CSV payouts (Connect not available there yet).')
                                                            : 'Direct Connect payouts are not yet enabled; everyone is paid by CSV for now.'}
                                                </div>
                                            </div>
                                            <div style={{ padding: '0.6rem 0.8rem', borderRadius: 8, background: '#161618', border: '1px solid #3a3a3c' }}>
                                                <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.06em', opacity: 0.7 }}>Declared country</div>
                                                <div style={{ fontWeight: 700, marginTop: '0.2rem' }}>{countryLabel}</div>
                                                <div style={{ fontSize: '0.75rem', opacity: 0.7, marginTop: '0.15rem' }}>
                                                    {data.declaredCountry ? 'Update below to change your payout rail.' : 'Set it in Payout Details below.'}
                                                </div>
                                            </div>
                                        </div>
                                        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
                                            <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: qualifierMet ? '#1f3d2b' : '#3d2b1f', color: qualifierMet ? '#22c55e' : '#f59e0b' }}>
                                                {qualifierMet ? 'Qualified' : 'Not qualified'} — {data.liveReferred}/{data.minLiveReferred} live referrals
                                            </span>
                                            <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: thresholdMet ? '#1f3d2b' : '#3d2b1f', color: thresholdMet ? '#22c55e' : '#f59e0b' }}>
                                                {thresholdMet ? 'Above payout threshold' : 'Below payout threshold'} — ${Number(data.accruedBalance).toFixed(2)} / $${Number(data.payoutThreshold).toFixed(2)}
                                            </span>
                                        </div>
                                        <p style={{ fontSize: '0.8rem', opacity: 0.75, marginTop: 0 }}>
                                            You need at least {data.minLiveReferred} live (paying) referrals before any fee accrues, and a payable balance of at least ${Number(data.payoutThreshold).toFixed(2)} before a payout runs.
                                            You'll be onboarded for payouts only once you reach the ${Number(data.payoutThreshold).toFixed(2)} threshold — not at signup.
                                        </p>
                                    </>
                                );
                            })()}
                        </div>

                        <div className="profile-form-card" style={{ marginBottom: '1.5rem' }}>
                            <div className="profile-section-title">Payout Details</div>
                            <p style={{ fontSize: '0.8rem', opacity: 0.7, marginTop: 0 }}>
                                Required before any payout: <strong>payable name</strong> and <strong>payout email</strong>. Balances accrue until you provide these.
                            </p>
                            {profile && (
                                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
                                    <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: profile.readyForPayout ? '#1f3d2b' : '#3d2b1f', color: profile.readyForPayout ? '#22c55e' : '#f59e0b' }}>
                                        {profile.readyForPayout ? 'Ready for payout' : 'Payouts paused — add name & email'}
                                    </span>
                                    <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', borderRadius: 6, background: profile.taxInfoComplete ? '#1f3d2b' : '#3d2b1f', color: profile.taxInfoComplete ? '#22c55e' : '#f59e0b' }}>
                                        {profile.taxInfoComplete ? 'Tax info on file' : 'Tax info incomplete'}
                                    </span>
                                </div>
                            )}
                            <form onSubmit={saveProfile} style={{ display: 'grid', gap: '0.6rem' }}>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.6rem' }}>
                                    <input name="payableName" placeholder="Payable name" value={profileForm.payableName ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                    <input name="payoutEmail" type="email" placeholder="Payout email" value={profileForm.payoutEmail ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                </div>
                                <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap' }}>
                                    <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem' }}>
                                        <input type="checkbox" name="international" checked={!!profileForm.international} onChange={handleProfileChange} /> I am international (no SSN/EIN required)
                                    </label>
                                    <label style={{ fontSize: '0.85rem' }}>Entity type:
                                        <select name="entityType" value={profileForm.entityType ?? 'INDIVIDUAL'} onChange={handleProfileChange} style={{ marginLeft: '0.4rem', ...inputStyle }}>
                                            <option value="INDIVIDUAL">Individual</option>
                                            <option value="CORPORATION">Corporation</option>
                                        </select>
                                    </label>
                                </div>
                                <input
                                    name="taxId"
                                    placeholder={profile?.hasTaxId ? `Tax ID on file (••••${profile.taxIdLast4 ?? ''}) — re-enter to replace` : 'SSN or EIN'}
                                    value={taxIdInput}
                                    onChange={e => setTaxIdInput(e.target.value)}
                                    style={inputStyle}
                                />
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.6rem' }}>
                                    <input name="addressLine1" placeholder="Address line 1" value={profileForm.addressLine1 ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                    <input name="addressLine2" placeholder="Address line 2" value={profileForm.addressLine2 ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                </div>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: '0.6rem' }}>
                                    <input name="city" placeholder="City" value={profileForm.city ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                    <input name="state" placeholder="State / Province" value={profileForm.state ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                    <input name="postalCode" placeholder="Postal code" value={profileForm.postalCode ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                    <input name="country" placeholder="Country" value={profileForm.country ?? ''} onChange={handleProfileChange} style={inputStyle} />
                                </div>
                                {profileMsg && (
                                    <div style={profileMsg.error ? { color: '#ef4444', fontSize: '0.85rem' } : { color: '#22c55e', fontSize: '0.85rem' }}>{profileMsg.text}</div>
                                )}
                                <div>
                                    <button className="btn-trade" disabled={profileSaving}>{profileSaving ? 'Saving…' : 'Save Payout Details'}</button>
                                </div>
                            </form>
                        </div>

                        <div className="profile-form-card">
                            <div className="profile-section-title">How it works</div>
                            <ul style={{ margin: 0, paddingLeft: '1.25rem', lineHeight: 1.7, opacity: 0.85 }}>
                                <li>Share your link. Anyone who signs up through it is attributed to you.</li>
                                <li>You need at least {data.minLiveReferred} live (paying) referrals before any fee accrues — no back-pay for earlier payments.</li>
                                <li>When a referral pays, you earn 30% of their payment (subtotal, after discounts, before tax). Annual plans are split into 12 monthly installments.</li>
                                <li>You keep earning 30% for the referral's first 12 months, starting at their first paid invoice.</li>
                                <li>Each installment is held for 31 days, then locks in. Refunds or lost disputes claw back the matching fee in full.</li>
                                <li>Declare your country in Payout Details — it picks your rail: {data.connectEnabled ? 'Stripe Connect (direct) for the US, CSV for everywhere else' : 'CSV for everyone for now'}.</li>
                                <li>Payouts run monthly on the last day. You're onboarded for payouts once your payable balance reaches ${Number(data.payoutThreshold).toFixed(2)}; below that, it rolls over to the next cycle.</li>
                            </ul>
                        </div>
                    </>
                )}
            </main>
        </div>
    );
};

export default Referral;
