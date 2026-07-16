import React, { useState, useEffect } from 'react';
import { getQuote } from '../api/marketApi';
import { submitTransaction, getCashBalance } from '../api/portfolioApi';
import type { TransactionRequest } from '../api/portfolioApi';
import CashForm from './CashForm';

interface Props {
    portfolioId: number;
    onClose: () => void;
    onSuccess: () => void;
    defaultSymbol?: string;
    defaultTradeType?: 'BUY' | 'SELL';
    lockSymbol?: boolean;
}

const TradeForm: React.FC<Props> = ({ portfolioId, onClose, onSuccess, defaultSymbol = '', defaultTradeType = 'BUY', lockSymbol = false }) => {
    const [symbol, setSymbol] = useState(defaultSymbol.toUpperCase());
    const [tradeType, setTradeType] = useState<'BUY' | 'SELL'>(defaultTradeType);
    const [inputMode, setInputMode] = useState<'shares' | 'dollars'>('shares');
    const [quantity, setQuantity] = useState('');
    const [dollarAmount, setDollarAmount] = useState('');
    const [price, setPrice] = useState('');
    const [quoteLoading, setQuoteLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [cashBalance, setCashBalance] = useState<number | null>(null);
    const [showCashModal, setShowCashModal] = useState(false);
    const [insufficientCash, setInsufficientCash] = useState(false);

    useEffect(() => {
        getCashBalance(portfolioId).then(setCashBalance).catch(() => {});
    }, [portfolioId]);

    useEffect(() => {
        if (lockSymbol && defaultSymbol) {
            setQuoteLoading(true);
            setError('');
            getQuote(defaultSymbol.toUpperCase())
                .then(quote => setPrice(quote.price.toString()))
                .catch(() => setError('Could not fetch quote for this symbol.'))
                .finally(() => setQuoteLoading(false));
        }
    }, []);

    const fmtCash = (n: number) =>
        `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

    const lookupQuote = async () => {
        if (!symbol.trim()) return;
        setQuoteLoading(true);
        setError('');
        try {
            const quote = await getQuote(symbol.trim().toUpperCase());
            setPrice(quote.price.toString());
        } catch {
            setError('Could not fetch quote for this symbol.');
        } finally {
            setQuoteLoading(false);
        }
    };

    const handleSymbolBlur = () => {
        if (symbol.trim()) lookupQuote();
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        const prc = parseFloat(price);
        if (!symbol.trim()) { setError('Symbol is required.'); return; }
        if (isNaN(prc) || prc <= 0) { setError('Price must be a positive number.'); return; }

        let qty: number;
        if (inputMode === 'dollars') {
            const dollars = parseFloat(dollarAmount);
            if (isNaN(dollars) || dollars <= 0) { setError('Dollar amount must be a positive number.'); return; }
            qty = dollars / prc;
        } else {
            qty = parseFloat(quantity);
            if (isNaN(qty) || qty <= 0) { setError('Quantity must be a positive number.'); return; }
        }

        if (tradeType === 'BUY') {
            const totalCost = qty * prc;
            try {
                const cash = await getCashBalance(portfolioId);
                setCashBalance(cash);
                if (cash < totalCost) {
                    setError(`Insufficient cash. Available: ${fmtCash(cash)}, Required: ${fmtCash(totalCost)}`);
                    setInsufficientCash(true);
                    return;
                }
            } catch {
                setError('Could not verify cash balance. Please try again.');
                return;
            }
        }

        const req: TransactionRequest = {
            symbol: symbol.trim().toUpperCase(),
            type: tradeType,
            quantity: qty,
            price: prc,
        };

        setSubmitting(true);
        try {
            await submitTransaction(req, portfolioId);
            onSuccess();
            onClose();
        } catch (err: any) {
            setError(err.response?.data?.message || 'Transaction failed. Please try again.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <>
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-card" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Place Trade</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>

                <div style={{ textAlign: 'center', marginBottom: '0.75rem', fontSize: '1.1rem' }}>
                    Available Cash:&nbsp;
                    <strong>
                        {cashBalance !== null ? fmtCash(cashBalance) : '—'}
                    </strong>
                </div>

                {error && (
                    <div>
                        <div className="error-msg">{error}</div>
                        {insufficientCash && (
                            <button
                                type="button"
                                onClick={() => setTimeout(() => setShowCashModal(true), 0)}
                                style={{
                                    display: 'block', width: '100%', marginBottom: '0.75rem',
                                    padding: '0.5rem', fontSize: '0.9rem', fontWeight: 600,
                                    background: 'var(--accent)', color: '#fff',
                                    border: 'none', borderRadius: 6, cursor: 'pointer',
                                }}
                            >
                                + Add Cash
                            </button>
                        )}
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    <div className="trade-type-toggle">
                        <button
                            type="button"
                            className={`toggle-btn ${tradeType === 'BUY' ? 'active-buy' : ''}`}
                            onClick={() => setTradeType('BUY')}
                        >
                            BUY
                        </button>
                        <button
                            type="button"
                            className={`toggle-btn ${tradeType === 'SELL' ? 'active-sell' : ''}`}
                            onClick={() => setTradeType('SELL')}
                        >
                            SELL
                        </button>
                    </div>

                    <label className="form-label">Symbol</label>
                    {lockSymbol ? (
                        <input
                            type="text"
                            value={symbol}
                            readOnly
                            style={{ background: 'var(--bg-card)', opacity: 0.7, cursor: 'default', marginBottom: '1rem' }}
                        />
                    ) : (
                        <div className="symbol-row">
                            <input
                                type="text"
                                placeholder="e.g. AAPL"
                                value={symbol}
                                onChange={e => setSymbol(e.target.value.toUpperCase())}
                                onBlur={handleSymbolBlur}
                            />
                            <button type="button" className="btn-lookup" onClick={lookupQuote} disabled={quoteLoading}>
                                {quoteLoading ? '…' : 'Get Price'}
                            </button>
                        </div>
                    )}

                    <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
                        <button
                            type="button"
                            onClick={() => setInputMode('shares')}
                            style={{
                                flex: 1, padding: '0.35rem 0', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', borderRadius: 4,
                                background: inputMode === 'shares' ? 'var(--accent)' : 'var(--bg-card)',
                                color: inputMode === 'shares' ? '#fff' : 'var(--text-gray)',
                                border: '1px solid var(--border)',
                            }}
                        ># of Shares</button>
                        <button
                            type="button"
                            onClick={() => setInputMode('dollars')}
                            style={{
                                flex: 1, padding: '0.35rem 0', fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', borderRadius: 4,
                                background: inputMode === 'dollars' ? 'var(--accent)' : 'var(--bg-card)',
                                color: inputMode === 'dollars' ? '#fff' : 'var(--text-gray)',
                                border: '1px solid var(--border)',
                            }}
                        >$ Amount</button>
                    </div>

                    {inputMode === 'shares' ? (
                        <>
                            <label className="form-label">Quantity</label>
                            <input
                                type="number"
                                placeholder="0"
                                min="0"
                                step="any"
                                value={quantity}
                                onChange={e => setQuantity(e.target.value)}
                                autoFocus={lockSymbol}
                            />
                        </>
                    ) : (
                        <>
                            <label className="form-label">Dollar Amount ($)</label>
                            <input
                                type="number"
                                placeholder="0.00"
                                min="0"
                                step="any"
                                value={dollarAmount}
                                onChange={e => setDollarAmount(e.target.value)}
                                autoFocus={lockSymbol}
                            />
                        </>
                    )}

                    <label className="form-label">Price per Share ($)</label>
                    <input
                        type="text"
                        value={quoteLoading ? 'Fetching…' : price}
                        readOnly
                        style={{ background: 'var(--bg-card)', opacity: 0.7, cursor: 'default', marginBottom: '1rem' }}
                    />

                    {price && (inputMode === 'shares' ? quantity : dollarAmount) && (
                        <div className="trade-total">
                            {inputMode === 'shares'
                                ? `Total: $${(parseFloat(quantity || '0') * parseFloat(price || '0')).toFixed(2)}`
                                : `Shares: ${(parseFloat(dollarAmount || '0') / parseFloat(price || '1')).toFixed(6)}`}
                        </div>
                    )}

                    <button type="submit" className="btn-primary-full" disabled={submitting}>
                        {submitting ? 'Submitting…' : `Confirm ${tradeType}`}
                    </button>
                </form>
            </div>
        </div>
        {showCashModal && (
            <CashForm
                portfolioId={portfolioId}
                onClose={() => setShowCashModal(false)}
                onSuccess={() => {
                    setShowCashModal(false);
                    getCashBalance(portfolioId).then(bal => {
                        setCashBalance(bal);
                        setInsufficientCash(false);
                        setError('');
                    }).catch(() => {});
                }}
            />
        )}
        </>
    );
};

export default TradeForm;
