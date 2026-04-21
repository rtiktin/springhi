import React, { useEffect, useState } from 'react';
import { getTransactions } from '../api/portfolioApi';
import type { Transaction } from '../api/portfolioApi';

const TransactionHistory: React.FC = () => {
    const [transactions, setTransactions] = useState<Transaction[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        getTransactions()
            .then(data => setTransactions(data.sort((a, b) =>
                new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
            )))
            .catch(() => setError('Failed to load transaction history.'))
            .finally(() => setLoading(false));
    }, []);

    if (loading) return <div className="portfolio-loading">Loading transactions…</div>;
    if (error) return <div className="error-msg">{error}</div>;
    if (transactions.length === 0) return (
        <div className="portfolio-loading">No transactions yet. Place your first trade!</div>
    );

    return (
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
                            <td className="symbol-cell">{t.symbol}</td>
                            <td className={t.type === 'BUY' ? 'positive' : 'negative'}>{t.type}</td>
                            <td>{t.quantity}</td>
                            <td>${t.price.toFixed(4)}</td>
                            <td>${(t.quantity * t.price).toFixed(2)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};

export default TransactionHistory;
