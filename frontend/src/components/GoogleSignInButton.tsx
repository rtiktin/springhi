import React, { useState } from 'react';
import { exchangeGoogleCode } from '../api/googleAuthApi';

interface Props {
    referralCode?: string;
    adCode?: string;
    onAuthSuccess: (token: string) => void;
    onError: (message: string) => void;
    label?: string;
}

const GoogleIcon: React.FC = () => (
    <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8c-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4C12.955 4 4 12.955 4 24s8.955 20 20 20s20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z" />
        <path fill="#FF3D00" d="M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4C16.318 4 9.656 8.337 6.306 14.691z" />
        <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z" />
        <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002l6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z" />
    </svg>
);

const GoogleSignInButton: React.FC<Props> = ({
    referralCode,
    adCode,
    onAuthSuccess,
    onError,
    label = 'Continue with Google',
}) => {
    const [loading, setLoading] = useState(false);

    const handleClick = () => {
        const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
        if (!clientId) {
            onError('Google sign-in is not configured.');
            return;
        }
        const oauth2 = window.google?.accounts?.oauth2;
        if (!oauth2) {
            onError('Google sign-in is not available right now. Please reload the page.');
            return;
        }
        setLoading(true);
        oauth2.initCodeClient({
            client_id: clientId,
            scope: 'openid email profile',
            ux_mode: 'popup',
            callback: async (response) => {
                if (!response.code) {
                    setLoading(false);
                    onError('Google did not return an authorization code.');
                    return;
                }
                try {
                    const res = await exchangeGoogleCode(response.code, referralCode, adCode);
                    setLoading(false);
                    onAuthSuccess(res.token);
                } catch (e: unknown) {
                    setLoading(false);
                    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
                        || 'Google sign-in failed.';
                    onError(msg);
                }
            },
            error_callback: () => {
                setLoading(false);
                onError('Google sign-in was canceled or failed.');
            },
        }).requestCode();
    };

    return (
        <button
            type="button"
            className="btn-primary-full"
            onClick={handleClick}
            disabled={loading}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
        >
            {loading ? 'Connecting…' : (<><GoogleIcon /> {label}</>)}
        </button>
    );
};

export default GoogleSignInButton;
