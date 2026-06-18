import React, { useState, useEffect } from 'react';
import { getQuote } from '../api/marketApi';
import { submitTransaction, getCashBalance } from '../api/portfolioApi';
import type { TransactionRequest } from '../api/portfolioApi';

interface Props {
    portfolioId: number;
    onClose: () => void;
    onSuccess: () => void;
    defaultSymbol?: string;
}

const TradeForm: React.FC<Props> = ({ portfolioId, onClose, onSuccess, defaultSymbol = '' }) => {
    const [symbol, setSymbol] = useState(defaultSymbol.toUpperCase());
    const [tradeType, setTradeType] = useState<'BUY' | 'SELL'>('BUY');
    const [quantity, setQuantity] = useState('');
    const [price, setPrice] = useState('');
    const [quoteLoading, setQuoteLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [cashBalance, setCashBalance] = useState<number | null>(null);

    useEffect(() => {
        getCashBalance(portfolioId).then(setCashBalance).catch(() => {});
    }, [portfolioId]);

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
        const qty = parseFloat(quantity);
        const prc = parseFloat(price);
        if (!symbol.trim()) { setError('Symbol is required.'); return; }
        if (isNaN(qty) || qty <= 0) { setError('Quantity must be a positive number.'); return; }
        if (isNaN(prc) || prc <= 0) { setError('Price must be a positive number.'); return; }

        if (tradeType === 'BUY') {
            const totalCost = qty * prc;
            try {
                const cash = await getCashBalance(portfolioId);
                setCashBalance(cash);
                if (cash < totalCost) {
                    setError(`Insufficient cash. Available: ${fmtCash(cash)}, Required: ${fmtCash(totalCost)}`);
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

                {error && <div className="error-msg">{error}</div>}

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

                    <label className="form-label">Quantity</label>
                    <input
                        type="number"
                        placeholder="0"
                        min="0"
                        step="any"
                        value={quantity}
                        onChange={e => setQuantity(e.target.value)}
                    />

                    <label className="form-label">Price per Share ($)</label>
                    <input
                        type="number"
                        placeholder="0.00"
                        min="0"
                        step="any"
                        value={price}
                        onChange={e => setPrice(e.target.value)}
                    />

                    {quantity && price && (
                        <div className="trade-total">
                            Total: ${(parseFloat(quantity || '0') * parseFloat(price || '0')).toFixed(2)}
                        </div>
                    )}

                    <button type="submit" className="btn-primary-full" disabled={submitting}>
                        {submitting ? 'Submitting…' : `Confirm ${tradeType}`}
                    </button>
                </form>
            </div>
        </div>
    );
};

export default TradeForm;
