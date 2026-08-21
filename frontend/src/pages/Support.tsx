import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { MessageSquare, ChevronDown, ChevronUp, Plus, Send } from 'lucide-react';
import { getLoggedInUsername } from '../utils/auth';
import {
    createTicket, getMyTickets, getTicketDetail, addUserReply,
} from '../api/supportApi';

interface TicketReply {
    id: number;
    responderName: string;
    adminReply: boolean;
    message: string;
    createdAt: string;
}
interface TicketSummary {
    id: number;
    subject: string;
    category: string;
    status: string;
    username: string;
    createdAt: string;
    updatedAt: string;
    replyCount: number;
}
interface TicketDetail extends TicketSummary {
    userId: number;
    message: string;
    replies: TicketReply[];
}
interface PageResult<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    number: number;
}

const STATUS_COLOR: Record<string, string> = {
    OPEN: '#60a5fa',
    IN_PROGRESS: '#f59e0b',
    RESOLVED: '#22c55e',
    CLOSED: '#6b7280',
};

const CATEGORIES = ['GENERAL', 'BILLING', 'BUG', 'FEATURE_REQUEST', 'ACCOUNT'];
const CATEGORY_LABEL: Record<string, string> = {
    GENERAL: 'General',
    BILLING: 'Billing',
    BUG: 'Bug Report',
    FEATURE_REQUEST: 'Feature Request',
    ACCOUNT: 'Account',
};

const Support: React.FC = () => {
    const [tickets, setTickets] = useState<TicketSummary[]>([]);
    const [totalPages, setTotalPages] = useState(0);
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [expandedId, setExpandedId] = useState<number | null>(null);
    const [expandedDetail, setExpandedDetail] = useState<TicketDetail | null>(null);
    const [expandedLoading, setExpandedLoading] = useState(false);
    const [replyText, setReplyText] = useState('');
    const [replying, setReplying] = useState(false);

    const [showNew, setShowNew] = useState(false);
    const [newSubject, setNewSubject] = useState('');
    const [newCategory, setNewCategory] = useState('GENERAL');
    const [newMessage, setNewMessage] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState('');

    const loadTickets = (p = 0) => {
        setLoading(true);
        getMyTickets(p, 10)
            .then((r: PageResult<TicketSummary>) => {
                setTickets(r.content);
                setTotalPages(r.totalPages);
                setPage(p);
            })
            .catch(() => {})
            .finally(() => setLoading(false));
    };

    useEffect(() => { loadTickets(); }, []);

    const toggleExpand = (id: number) => {
        if (expandedId === id) { setExpandedId(null); setExpandedDetail(null); return; }
        setExpandedId(id);
        setExpandedDetail(null);
        setExpandedLoading(true);
        getTicketDetail(id)
            .then(setExpandedDetail)
            .catch(() => {})
            .finally(() => setExpandedLoading(false));
    };

    const handleReply = async () => {
        if (!replyText.trim() || !expandedId) return;
        setReplying(true);
        try {
            const updated = await addUserReply(expandedId, replyText.trim());
            setExpandedDetail(updated);
            setReplyText('');
        } catch (err) {
            console.error('Failed to add user reply:', err);
        } finally { setReplying(false); }
    };

    const handleSubmit = async () => {
        if (!newSubject.trim() || !newMessage.trim()) { setSubmitError('Subject and message are required.'); return; }
        setSubmitting(true);
        setSubmitError('');
        try {
            await createTicket(newSubject.trim(), newCategory, newMessage.trim());
            setShowNew(false);
            setNewSubject(''); setNewCategory('GENERAL'); setNewMessage('');
            loadTickets(0);
        } catch (err) {
            console.error('Failed to submit ticket:', err);
            setSubmitError('Failed to submit ticket.');
        } finally { setSubmitting(false); }
    };

    const fmtDate = (s: string) => new Date(s).toLocaleString();

    const username = getLoggedInUsername();

    return (
        <div style={{ fontFamily: "'Inter', system-ui, sans-serif", background: '#0a0a0b', color: '#fff', minHeight: '100vh' }}>
            <header className="navbar">
                <div className="navbar-brand" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
                    <Link to="/" className="logo">SpringHi.ai</Link>
                    {username && <span className="nav-welcome" style={{ fontSize: '0.75rem', marginTop: '-0.2rem', opacity: 0.8 }}>Welcome back, {username}</span>}
                </div>
                <nav style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <Link to="/getting-started" className="nav-link">Getting Started</Link>
                    <Link to="/portfolio" className="nav-link">My Portfolio</Link>
                    <Link to="/subscription" className="nav-link">Subscription</Link>
                </nav>
            </header>

            <main style={{ padding: '4rem 8% 6rem', maxWidth: 860, margin: '0 auto' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2rem', flexWrap: 'wrap', gap: '1rem' }}>
                    <div>
                        <h1 style={{ fontSize: '1.9rem', fontWeight: 800, margin: 0 }}>Customer Support</h1>
                        <p style={{ color: '#a0a0a0', margin: '0.4rem 0 0', fontSize: '0.95rem' }}>Submit a request or follow up on an existing ticket.</p>
                    </div>
                    <button
                        onClick={() => setShowNew(v => !v)}
                        style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: '#6366f1', color: '#fff', border: 'none', borderRadius: 9, padding: '0.65rem 1.25rem', fontWeight: 700, fontSize: '0.9rem', cursor: 'pointer' }}
                    >
                        <Plus size={16} /> New Ticket
                    </button>
                </div>

                {showNew && (
                    <div style={{ background: '#161618', border: '1px solid #2a2a2c', borderRadius: 14, padding: '1.5rem', marginBottom: '2rem' }}>
                        <h2 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '1.25rem' }}>Submit a support ticket</h2>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1rem' }}>
                            <div>
                                <label style={{ display: 'block', fontSize: '0.82rem', color: '#a0a0a0', marginBottom: 4 }}>Subject *</label>
                                <input
                                    value={newSubject}
                                    onChange={e => setNewSubject(e.target.value)}
                                    placeholder="Brief description of your issue"
                                    style={{ width: '100%', background: '#0e0e10', border: '1px solid #2a2a2c', borderRadius: 8, padding: '0.55rem 0.75rem', color: '#fff', fontSize: '0.9rem', boxSizing: 'border-box' }}
                                />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '0.82rem', color: '#a0a0a0', marginBottom: 4 }}>Category</label>
                                <select
                                    value={newCategory}
                                    onChange={e => setNewCategory(e.target.value)}
                                    style={{ width: '100%', background: '#0e0e10', border: '1px solid #2a2a2c', borderRadius: 8, padding: '0.55rem 0.75rem', color: '#fff', fontSize: '0.9rem' }}
                                >
                                    {CATEGORIES.map(c => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
                                </select>
                            </div>
                        </div>
                        <div style={{ marginBottom: '1rem' }}>
                            <label style={{ display: 'block', fontSize: '0.82rem', color: '#a0a0a0', marginBottom: 4 }}>Message *</label>
                            <textarea
                                value={newMessage}
                                onChange={e => setNewMessage(e.target.value)}
                                rows={5}
                                placeholder="Describe your issue in detail…"
                                style={{ width: '100%', background: '#0e0e10', border: '1px solid #2a2a2c', borderRadius: 8, padding: '0.55rem 0.75rem', color: '#fff', fontSize: '0.9rem', resize: 'vertical', boxSizing: 'border-box' }}
                            />
                        </div>
                        {submitError && <div style={{ color: '#f87171', fontSize: '0.85rem', marginBottom: '0.75rem' }}>{submitError}</div>}
                        <div style={{ display: 'flex', gap: '0.75rem' }}>
                            <button onClick={handleSubmit} disabled={submitting} style={{ background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, padding: '0.6rem 1.4rem', fontWeight: 700, fontSize: '0.9rem', cursor: 'pointer' }}>
                                {submitting ? 'Submitting…' : 'Submit Ticket'}
                            </button>
                            <button onClick={() => setShowNew(false)} style={{ background: 'transparent', color: '#a0a0a0', border: '1px solid #3a3a3c', borderRadius: 8, padding: '0.6rem 1.1rem', fontSize: '0.9rem', cursor: 'pointer' }}>
                                Cancel
                            </button>
                        </div>
                    </div>
                )}

                {loading ? (
                    <div style={{ textAlign: 'center', color: '#a0a0a0', padding: '3rem' }}>Loading tickets…</div>
                ) : tickets.length === 0 ? (
                    <div style={{ textAlign: 'center', color: '#a0a0a0', padding: '3rem', background: '#161618', border: '1px solid #2a2a2c', borderRadius: 14 }}>
                        <MessageSquare size={40} color="#3a3a3c" style={{ marginBottom: '1rem' }} />
                        <div>No support tickets yet. Click <strong>New Ticket</strong> to get help.</div>
                    </div>
                ) : (
                    <>
                        {tickets.map(t => {
                            const isOpen = expandedId === t.id;
                            return (
                                <div key={t.id} style={{ background: '#161618', border: '1px solid #2a2a2c', borderRadius: 12, marginBottom: '1rem', overflow: 'hidden' }}>
                                    <div
                                        onClick={() => toggleExpand(t.id)}
                                        style={{ display: 'flex', alignItems: 'center', padding: '1rem 1.25rem', cursor: 'pointer', gap: '0.75rem', userSelect: 'none' }}
                                    >
                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{ fontWeight: 700, fontSize: '0.95rem', marginBottom: '0.25rem' }}>{t.subject}</div>
                                            <div style={{ fontSize: '0.78rem', color: '#a0a0a0' }}>
                                                {CATEGORY_LABEL[t.category] ?? t.category} · {fmtDate(t.createdAt)}
                                                {t.replyCount > 0 && ` · ${t.replyCount} ${t.replyCount === 1 ? 'reply' : 'replies'}`}
                                            </div>
                                        </div>
                                        <span style={{ background: STATUS_COLOR[t.status] ?? '#6b7280', color: '#fff', borderRadius: 5, padding: '0.15rem 0.6rem', fontSize: '0.75rem', fontWeight: 700, whiteSpace: 'nowrap' }}>
                                            {t.status.replace('_', ' ')}
                                        </span>
                                        {isOpen ? <ChevronUp size={16} color="#a0a0a0" /> : <ChevronDown size={16} color="#a0a0a0" />}
                                    </div>

                                    {isOpen && (
                                        <div style={{ borderTop: '1px solid #2a2a2c', padding: '1.25rem' }}>
                                            {expandedLoading ? (
                                                <div style={{ color: '#a0a0a0', fontSize: '0.875rem' }}>Loading…</div>
                                            ) : expandedDetail ? (
                                                <>
                                                    <div style={{ background: '#0e0e10', borderRadius: 8, padding: '1rem', fontSize: '0.875rem', color: '#d1d5db', lineHeight: 1.65, marginBottom: '1.25rem', whiteSpace: 'pre-wrap' }}>
                                                        {expandedDetail.message}
                                                    </div>

                                                    {expandedDetail.replies.length > 0 && (
                                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.25rem' }}>
                                                            {expandedDetail.replies.map(r => (
                                                                <div key={r.id} style={{
                                                                    background: r.adminReply ? 'rgba(99,102,241,0.1)' : '#0e0e10',
                                                                    border: `1px solid ${r.adminReply ? 'rgba(99,102,241,0.3)' : '#2a2a2c'}`,
                                                                    borderRadius: 8, padding: '0.85rem 1rem',
                                                                }}>
                                                                    <div style={{ fontSize: '0.78rem', color: r.adminReply ? '#a78bfa' : '#a0a0a0', marginBottom: '0.4rem', fontWeight: 600 }}>
                                                                        {r.adminReply ? '🛡 Support Team' : r.responderName} · {fmtDate(r.createdAt)}
                                                                    </div>
                                                                    <div style={{ fontSize: '0.875rem', color: '#d1d5db', lineHeight: 1.65, whiteSpace: 'pre-wrap' }}>{r.message}</div>
                                                                </div>
                                                            ))}
                                                        </div>
                                                    )}

                                                    {expandedDetail.status !== 'CLOSED' && expandedDetail.status !== 'RESOLVED' && (
                                                        <div style={{ display: 'flex', gap: '0.6rem' }}>
                                                            <textarea
                                                                value={replyText}
                                                                onChange={e => setReplyText(e.target.value)}
                                                                rows={3}
                                                                placeholder="Add a reply or more details…"
                                                                style={{ flex: 1, background: '#0e0e10', border: '1px solid #2a2a2c', borderRadius: 8, padding: '0.55rem 0.75rem', color: '#fff', fontSize: '0.875rem', resize: 'vertical' }}
                                                            />
                                                            <button
                                                                onClick={handleReply}
                                                                disabled={replying || !replyText.trim()}
                                                                style={{ background: '#6366f1', color: '#fff', border: 'none', borderRadius: 8, padding: '0 1rem', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, fontSize: '0.875rem', opacity: replying || !replyText.trim() ? 0.5 : 1 }}
                                                            >
                                                                <Send size={14} /> Send
                                                            </button>
                                                        </div>
                                                    )}
                                                </>
                                            ) : null}
                                        </div>
                                    )}
                                </div>
                            );
                        })}

                        {totalPages > 1 && (
                            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginTop: '1rem' }}>
                                <button onClick={() => loadTickets(page - 1)} disabled={page === 0} style={{ background: '#161618', border: '1px solid #2a2a2c', color: page === 0 ? '#4a4a4c' : '#fff', borderRadius: 7, padding: '0.4rem 0.9rem', cursor: page === 0 ? 'default' : 'pointer' }}>
                                    ← Prev
                                </button>
                                <span style={{ alignSelf: 'center', color: '#a0a0a0', fontSize: '0.875rem' }}>Page {page + 1} of {totalPages}</span>
                                <button onClick={() => loadTickets(page + 1)} disabled={page >= totalPages - 1} style={{ background: '#161618', border: '1px solid #2a2a2c', color: page >= totalPages - 1 ? '#4a4a4c' : '#fff', borderRadius: 7, padding: '0.4rem 0.9rem', cursor: page >= totalPages - 1 ? 'default' : 'pointer' }}>
                                    Next →
                                </button>
                            </div>
                        )}
                    </>
                )}
            </main>

            <footer style={{ textAlign: 'center', padding: '2.5rem', color: '#4a4a4c', borderTop: '1px solid #1e1e20', fontSize: '0.85rem' }}>
                &copy; {new Date().getFullYear()} SpringHi.ai
            </footer>
        </div>
    );
};

export default Support;
