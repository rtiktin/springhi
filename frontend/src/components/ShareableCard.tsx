import React, { useRef, useState } from 'react';
import { toPng } from 'html-to-image';
import type { AssetWithPrice } from '../api/portfolioApi';

interface ShareableCardProps {
    portfolioName: string;
    username: string | null;
    aiProvider: string | null;
    rank: number | null;
    totalUsers: number | null;
    twrPercent: number;
    marginVsSpy: number | null;
    confidenceScore: number | null;
    holdings: AssetWithPrice[];
    competitionMonth: string | null;
    createdAt: string;
    hideRank?: boolean;
    goal?: string;
    onClose: () => void;
}

const ShareableCard: React.FC<ShareableCardProps> = ({
    portfolioName,
    username,
    aiProvider,
    rank,
    totalUsers,
    twrPercent,
    marginVsSpy,
    confidenceScore,
    holdings,
    competitionMonth,
    createdAt,
    hideRank = false,
    goal,
    onClose,
}) => {
    const cardRef = useRef<HTMLDivElement>(null);
    const [exporting, setExporting] = useState(false);

    const handleDownload = async () => {
        if (!cardRef.current) return;
        setExporting(true);
        try {
            const dataUrl = await toPng(cardRef.current, { cacheBust: true, pixelRatio: 2 });
            const link = document.createElement('a');
            link.download = `portfolio-share-${portfolioName.toLowerCase().replace(/\s+/g, '-')}.png`;
            link.href = dataUrl;
            link.click();
        } catch (err) {
            console.error('Export failed', err);
        } finally {
            setExporting(false);
        }
    };

    const topHoldings = holdings
        .slice()
        .sort((a, b) => (b.marketValue || 0) - (a.marketValue || 0))
        .slice(0, 4);

    const aiLabel = (p: string | null) => {
        const prov = p?.toLowerCase();
        if (prov === 'claude') return 'Claude AI';
        if (prov === 'chatgpt') return 'ChatGPT-4o';
        if (prov === 'gemini') return 'Gemini 2.0';
        return 'AI-Optimized';
    };

    const percentile = rank && totalUsers ? Math.max(1, Math.round((rank / totalUsers) * 100)) : null;

    const getDurationLabel = () => {
        const start = new Date(createdAt);
        const now = new Date();
        const diffMs = now.getTime() - start.getTime();
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        
        if (diffDays < 30) return `${diffDays} days active`;
        const diffMonths = Math.floor(diffDays / 30);
        if (diffMonths < 12) return `${diffMonths} ${diffMonths === 1 ? 'month' : 'months'} active`;
        const diffYears = (diffDays / 365).toFixed(1);
        return `${diffYears} ${Number(diffYears) === 1 ? 'year' : 'years'} active`;
    };

    return (
        <div className="modal-overlay" style={{ background: 'rgba(0,0,0,0.85)', zIndex: 3000 }}>
            <div className="modal-card" style={{ maxWidth: 500, padding: 0, overflow: 'hidden', background: '#0a0b10' }}>
                <div style={{ padding: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #1f2937' }}>
                    <h3 style={{ margin: 0, fontSize: '1.1rem', color: '#fff' }}>Share Your Success</h3>
                    <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: '#9ca3af', fontSize: '1.25rem', cursor: 'pointer' }}>✕</button>
                </div>

                <div style={{ padding: '2rem' }}>
                    <div 
                        ref={cardRef}
                        style={{
                            width: 400,
                            height: 400,
                            margin: '0 auto',
                            background: 'linear-gradient(135deg, #1e1b4b 0%, #0f172a 100%)',
                            borderRadius: 24,
                            padding: '2rem',
                            display: 'flex',
                            flexDirection: 'column',
                            position: 'relative',
                            boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)',
                            color: '#fff',
                            fontFamily: 'system-ui, -apple-system, sans-serif'
                        }}
                    >
                        {/* Platform Logo */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.5rem' }}>
                            <div style={{ fontSize: '1.25rem', fontWeight: 800, letterSpacing: '-0.025em' }}>
                                SpringHi<span style={{ color: '#818cf8' }}>.ai</span>
                            </div>
                            <div style={{ 
                                background: 'rgba(129, 140, 248, 0.1)', 
                                border: '1px solid rgba(129, 140, 248, 0.2)',
                                borderRadius: 8,
                                padding: '0.3rem 0.6rem',
                                fontSize: '0.7rem',
                                fontWeight: 600,
                                color: '#a5b4fc',
                                textTransform: 'uppercase'
                            }}>
                                {competitionMonth ? `${new Date(competitionMonth).toLocaleString('default', { month: 'long' })} League` : 'Active Portfolio'}
                            </div>
                        </div>

                        {/* Portfolio Name & AI */}
                        <div style={{ marginBottom: '2rem' }}>
                            <h4 style={{ margin: 0, fontSize: '1.5rem', fontWeight: 700, lineHeight: 1.2, marginBottom: '0.1rem' }}>{portfolioName}</h4>
                            {username && (
                                <div style={{ fontSize: '0.85rem', color: '#818cf8', fontWeight: 600, marginBottom: '0.4rem' }}>
                                    @{username}
                                </div>
                            )}
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#94a3b8', fontSize: '0.85rem' }}>
                                    <span>Powered by</span>
                                    <span style={{ color: '#e2e8f0', fontWeight: 600 }}>{aiLabel(aiProvider)}</span>
                                </div>
                                <div style={{ color: '#94a3b8', fontSize: '0.75rem', fontWeight: 500 }}>
                                    {getDurationLabel()}
                                </div>
                            </div>
                        </div>

                        {/* Main Metrics */}
                        <div style={{ display: 'flex', gap: '2rem', marginBottom: '2rem' }}>
                            <div>
                                <div style={{ fontSize: '0.75rem', color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.25rem' }}>Total Return</div>
                                <div style={{ fontSize: '2rem', fontWeight: 800, color: twrPercent >= 0 ? '#4ade80' : '#f87171' }}>
                                    {twrPercent >= 0 ? '+' : ''}{twrPercent.toFixed(2)}%
                                </div>
                            </div>
                            {marginVsSpy != null && marginVsSpy > 0 && (
                                <div>
                                    <div style={{ fontSize: '0.75rem', color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.25rem' }}>Vs S&P 500</div>
                                    <div style={{ fontSize: '2rem', fontWeight: 800, color: marginVsSpy >= 0 ? '#4ade80' : '#f87171' }}>
                                        {marginVsSpy >= 0 ? '+' : ''}{marginVsSpy.toFixed(1)}%
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Rank / Top Holdings */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: 'auto' }}>
                            <div>
                                {rank && !hideRank && (
                                    <div style={{ marginBottom: '0.75rem' }}>
                                        <div style={{ fontSize: '0.7rem', color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.1rem' }}>
                                            Global Rank {goal && goal !== '' ? `(${goal.charAt(0).toUpperCase() + goal.slice(1)})` : ''}
                                        </div>
                                        <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>#{rank} {percentile && <span style={{ fontSize: '0.85rem', color: '#818cf8', fontWeight: 500 }}>• Top {percentile}%</span>}</div>
                                    </div>
                                )}
                                {confidenceScore != null && (
                                    <div>
                                        <div style={{ fontSize: '0.7rem', color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.1rem' }}>AI Confidence</div>
                                        <div style={{ fontSize: '1rem', fontWeight: 700, color: '#a78bfa' }}>🎯 {confidenceScore}%</div>
                                    </div>
                                )}
                            </div>
                            
                            <div style={{ textAlign: 'right' }}>
                                <div style={{ fontSize: '0.7rem', color: '#94a3b8', fontWeight: 600, textTransform: 'uppercase', marginBottom: '0.5rem' }}>Top Holdings</div>
                                <div style={{ display: 'flex', gap: '0.4rem', justifyContent: 'flex-end' }}>
                                    {topHoldings.map(h => (
                                        <div key={h.symbol} style={{ 
                                            background: 'rgba(255,255,255,0.05)', 
                                            borderRadius: 6, 
                                            padding: '0.3rem 0.5rem', 
                                            fontSize: '0.75rem', 
                                            fontWeight: 700,
                                            border: '1px solid rgba(255,255,255,0.1)'
                                        }}>
                                            {h.symbol}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>

                        {/* Branding Footer */}
                        <div style={{ position: 'absolute', bottom: 12, left: '50%', transform: 'translateX(-50%)', fontSize: '0.6rem', color: 'rgba(148, 163, 184, 0.5)', whiteSpace: 'nowrap' }}>
                            Verified results at springhi.ai • {new Date().toLocaleDateString()}
                        </div>
                    </div>
                </div>

                <div style={{ padding: '1.5rem', background: '#111827', display: 'flex', gap: '1rem' }}>
                    <button 
                        onClick={handleDownload}
                        disabled={exporting}
                        style={{ 
                            flex: 1, 
                            background: '#6c47ff', 
                            color: '#fff', 
                            border: 'none', 
                            borderRadius: 10, 
                            padding: '0.8rem', 
                            fontWeight: 700, 
                            cursor: exporting ? 'default' : 'pointer',
                            opacity: exporting ? 0.7 : 1
                        }}
                    >
                        {exporting ? 'Generating Image...' : 'Download Image for Social Media'}
                    </button>
                    <button 
                        onClick={onClose}
                        style={{ 
                            background: 'transparent', 
                            color: '#9ca3af', 
                            border: '1px solid #374151', 
                            borderRadius: 10, 
                            padding: '0.8rem 1.25rem', 
                            fontWeight: 600, 
                            cursor: 'pointer' 
                        }}
                    >
                        Close
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ShareableCard;
