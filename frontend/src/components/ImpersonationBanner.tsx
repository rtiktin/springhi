import React from 'react';
import { useNavigate } from 'react-router-dom';
import { isImpersonating, stopImpersonation, getLoggedInUsername } from '../utils/auth';

const ImpersonationBanner: React.FC = () => {
    const navigate = useNavigate();

    if (!isImpersonating()) return null;

    const impersonatedUser = getLoggedInUsername();

    const handleReturn = () => {
        stopImpersonation();
        navigate('/admin');
    };

    return (
        <div style={{
            background: '#6c47ff',
            color: '#fff',
            padding: '0.5rem 1.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '1rem',
            fontSize: '0.9rem',
            fontWeight: 500,
            position: 'sticky',
            top: 0,
            zIndex: 1000,
        }}>
            <span>⚠️ You are currently viewing as <strong>{impersonatedUser}</strong></span>
            <button
                onClick={handleReturn}
                style={{
                    background: '#fff',
                    color: '#6c47ff',
                    border: 'none',
                    borderRadius: 6,
                    padding: '0.25rem 0.75rem',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    cursor: 'pointer',
                }}
            >
                Return to Admin
            </button>
        </div>
    );
};

export default ImpersonationBanner;
