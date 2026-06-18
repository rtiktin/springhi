import React, { useState, useEffect } from 'react';
import { getTransactions, getCompanyName } from '../api/portfolioApi';
import type { Transaction } from '../api/portfolioApi';

interface Props {
    portfolioId: number;
}

const TransactionHistory: React.FC<Props> = ({ portfolioId }) => {
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
    const [companyNameMap, setCompanyNameMap] = useState<Record<string, string | null>>({});
    const [lookingUp, setLookingUp] = useState(false);

    useEffect(() => {
        setLoading(true);
        getTransactions(portfolioId)
            .then(data => setTransactions(data.sort((a, b) =>
                new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
            )))
            .catch(() => setError('Failed to load transaction history.'))
            .finally(() => setLoading(false));
    }, [portfolioId]);

    const handleSymbolClick = (symbol: string) => {
        if (!symbol || symbol === 'CASH') return;
        setSelectedSymbol(symbol);
        setLookingUp(true);
        getCompanyName(symbol, portfolioId)
            .then(name => setCompanyNameMap(prev => ({ ...prev, [symbol]: name })))
            .catch(() => setCompanyNameMap(prev => ({ ...prev, [symbol]: null })))
            .finally(() => setLookingUp(false));
    };

    if (loading) return <div className="portfolio-loading">Loading transactions…</div>;
    if (error) return <div className="error-msg">{error}</div>;
    if (transactions.length === 0) return (
        <div className="portfolio-loading">No transactions yet. Place your first trade!</div>
    );

    const resolvedName = selectedSymbol != null ? companyNameMap[selectedSymbol] : undefined;

    return (
        <div>
            {selectedSymbol && (
                <div style={{
                    padding: '0.5rem 1rem',
                    marginBottom: '0.75rem',
                    background: 'var(--bg-card)',
                    borderRadius: '6px',
                    fontSize: '0.9rem',
                    color: 'var(--text-gray)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
                }}>
                    <span className="symbol-cell">{selectedSymbol}</span>
                    {lookingUp
                        ? <span>Looking up…</span>
                        : resolvedName
                            ? <span style={{ color: 'var(--text-primary)' }}>{resolvedName}</span>
                            : resolvedName === null
                                ? <span>Company name not found</span>
                                : null
                    }
                </div>
            )}
            <div className="holdings-table-wrap">
                <table className="holdings-table">
                    <thead>
                        <tr>
                            <th>Date &amp; Time</th>
                            <th>Symbol</th>
                            <th>Type</th>
                            <th>Quantity</th>
                            <th>Price</th>
                            <th>Total Value</th>
                        </tr>
                    </thead>
                    <tbody>
                        {transactions.map(t => (
                            <tr key={t.id}>
                                <td>{new Date(t.timestamp).toLocaleString()}</td>
                                <td
                                    className="symbol-cell"
                                    style={t.symbol !== 'CASH' ? { cursor: 'pointer', textDecoration: 'underline dotted' } : {}}
                                    title={t.symbol !== 'CASH' ? 'Click to look up company name' : undefined}
                                    onClick={() => handleSymbolClick(t.symbol)}
                                >
                                    {t.symbol}
                                </td>
                                <td className={t.type === 'BUY' ? 'positive' : 'negative'}>{t.type}</td>
                                <td>{t.quantity}</td>
                                <td>${t.price.toFixed(4)}</td>
                                <td>${(t.quantity * t.price).toFixed(2)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

export default TransactionHistory;
