import React, { useEffect, useRef } from 'react';
import { createChart, LineSeries } from 'lightweight-charts';
import type { PortfolioSnapshot } from '../api/portfolioApi';

interface Props {
    snapshots: PortfolioSnapshot[];
}

const PortfolioChart: React.FC<Props> = ({ snapshots }) => {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!containerRef.current) return;

        const data = snapshots
            .slice()
            .sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate))
            .map(s => ({ time: s.snapshotDate, value: s.totalValue }));

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
            rightPriceScale: { borderColor: '#2a2a2c' },
            timeScale: { borderColor: '#2a2a2c' },
            localization: {
                priceFormatter: (p: number) => '$' + p.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
            },
        });

        const lineSeries = chart.addSeries(LineSeries, {
            color: '#22c55e',
            lineWidth: 2,
        });

        lineSeries.setData(data);
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
    }, [snapshots]);

    return (
        <div className="chart-card">
            <h3 className="chart-title">Portfolio Value — Performance History</h3>
            {snapshots.length === 0 ? (
                <div className="chart-empty">
                    No history yet. Snapshots are taken automatically at 4:05pm ET on market days, or click "Snapshot Now" to record today's value.
                </div>
            ) : (
                <div ref={containerRef} />
            )}
        </div>
    );
};

export default PortfolioChart;
