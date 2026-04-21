import React, { useState } from 'react';
import { submitTransaction } from '../api/portfolioApi';

interface Props {
    onClose: () => void;
    onSuccess: () => void;
}

const CashForm: React.FC<Props> = ({ onClose, onSuccess }) => {
    const [mode, setMode] = useState<'DEPOSIT' | 'WITHDRAWAL'>('DEPOSIT');
    const [amount, setAmount] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        const amt = parseFloat(amount);
        if (isNaN(amt) || amt <= 0) { setError('Amount must be a positive number.'); return; }

        setSubmitting(true);
        try {
            await submitTransaction({ symbol: 'CASH', type: mode, quantity: amt, price: 1 });
            onSuccess();
            onClose();
        } catch (err: any) {
            setError(err.response?.data?.message || 'Transaction failed. Please try again.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-card" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Cash</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>

                {error && <div className="error-msg">{error}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="trade-type-toggle">
                        <button
                            type="button"
                            className={`toggle-btn ${mode === 'DEPOSIT' ? 'active-buy' : ''}`}
                            onClick={() => setMode('DEPOSIT')}
                        >
                            Deposit to Investment Account
                        </button>
                        <button
                            type="button"
                            className={`toggle-btn ${mode === 'WITHDRAWAL' ? 'active-sell' : ''}`}
                            onClick={() => setMode('WITHDRAWAL')}
                        >
                            Withdraw from Investment Account
                        </button>
                    </div>

                    <label className="form-label">Amount ($)</label>
                    <input
                        type="number"
                        placeholder="0.00"
                        min="0.01"
                        step="any"
                        value={amount}
                        onChange={e => setAmount(e.target.value)}
                        autoFocus
                    />

                    <button type="submit" className="btn-primary-full" disabled={submitting}>
                        {submitting ? 'Submitting…' : `Confirm ${mode === 'DEPOSIT' ? 'Deposit' : 'Withdrawal'}`}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default CashForm;
