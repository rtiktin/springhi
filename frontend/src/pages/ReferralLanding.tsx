import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { validateReferralCode, recordReferralClick } from '../api/referralApi';

const ReferralLanding: React.FC = () => {
    const { code } = useParams<{ code: string }>();
    const navigate = useNavigate();
    const [referrer, setReferrer] = useState<string | null>(null);
    const [status, setStatus] = useState<'loading' | 'invalid' | 'ok'>('loading');

    useEffect(() => {
        if (!code) {
            navigate('/signup', { replace: true });
            return;
        }
        localStorage.setItem('referralCode', code);
        recordReferralClick(code);
        let cancelled = false;
        validateReferralCode(code).then((res) => {
            if (cancelled) return;
            if (res) {
                setReferrer(res.referrerUsername);
                setStatus('ok');
            } else {
                setStatus('invalid');
            }
            setTimeout(
                () => navigate('/signup?ref=' + encodeURIComponent(code), { replace: true }),
                1200
            );
        });
        return () => { cancelled = true; };
    }, [code, navigate]);

    return (
        <div style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', padding: '2rem' }}>
            <div>
                <h1 style={{ fontSize: '1.75rem', marginBottom: '0.5rem' }}>SpringHi.ai</h1>
                {status === 'loading' && <p>Checking your referral…</p>}
                {status === 'ok' && <p>You've been invited by <strong>@{referrer}</strong>. Taking you to sign up…</p>}
                {status === 'invalid' && <p>Taking you to sign up…</p>}
            </div>
        </div>
    );
};

export default ReferralLanding;
