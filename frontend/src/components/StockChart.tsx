import React, { useEffect, useRef } from 'react';
import { createChart, LineSeries } from 'lightweight-charts';
import type { QuoteResponse } from '../api/marketApi';

interface Props {
    symbol: string;
    companyName?: string;
    data: QuoteResponse[];
}

const StockChart: React.FC<Props> = ({ symbol, companyName, data }) => {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!containerRef.current) return;

        const deduplicated = Array.from(
            data.reduce((map, q) => {
                const existing = map.get(q.tradingDay);
                if (!existing || q.fetchedAt > existing.fetchedAt) {
                    map.set(q.tradingDay, q);
                }
                return map;
            }, new Map<string, QuoteResponse>()).values()
        )
            .sort((a, b) => a.tradingDay.localeCompare(b.tradingDay))
            .map(q => ({ time: q.tradingDay, value: q.price }));

        const chart = createChart(containerRef.current, {
            width: containerRef.current.clientWidth,
            height: 280,
            layout: {
                background: { color: '#161618' },
                textColor: '#a0a0a0',
                attributionLogo: false,
            },
            grid: {
                vertLines: { color: '#2a2a2c' },
                horzLines: { color: '#2a2a2c' },
            },
            rightPriceScale: {
                borderColor: '#2a2a2c',
            },
            timeScale: {
                borderColor: '#2a2a2c',
            },
        });

        const lineSeries = chart.addSeries(LineSeries, {
            color: '#0066ff',
            lineWidth: 2,
        });

        lineSeries.setData(deduplicated);
        chart.timeScale().fitContent();

        const handleResize = () => {
            if (containerRef.current) {
                chart.applyOptions({ width: containerRef.current.clientWidth });
            }
        };
        window.addEventListener('resize', handleResize);

        return () => {
            window.removeEventListener('resize', handleResize);
            chart.remove();
        };
    }, [data]);

    return (
        <div className="chart-card">
            <h3 className="chart-title">
                {companyName ? `${companyName} (${symbol})` : symbol} — Price History
            </h3>
            {data.length === 0 ? (
                <div className="chart-empty">
                    No price history yet. Data accumulates each scheduled refresh (9am &amp; 3pm ET on weekdays).
                </div>
            ) : (
                <div ref={containerRef} />
            )}
        </div>
    );
};

export default StockChart;
