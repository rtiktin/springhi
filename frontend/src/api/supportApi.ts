import axios from 'axios';
import API_GATEWAY from './apiBase';

const BASE = `${API_GATEWAY}/api/v1/support`;
const ADMIN_BASE = `${API_GATEWAY}/api/v1/admin/support`;
const authHeader = () => ({ Authorization: `Bearer ${localStorage.getItem('token')}` });

export interface TicketReply {
    id: number;
    responderName: string;
    adminReply: boolean;
    message: string;
    createdAt: string;
}

export interface TicketSummary {
    id: number;
    subject: string;
    category: string;
    status: string;
    username: string;
    createdAt: string;
    updatedAt: string;
    replyCount: number;
}

export interface TicketDetail extends TicketSummary {
    userId: number;
    message: string;
    replies: TicketReply[];
}

export interface PageResult<T> {
    content: T[];
    totalPages: number;
    totalElements: number;
    number: number;
}

export const createTicket = async (subject: string, category: string, message: string): Promise<TicketDetail> => {
    const res = await axios.post(`${BASE}/tickets`, { subject, category, message }, { headers: authHeader() });
    return res.data;
};

export const getMyTickets = async (page = 0, size = 10): Promise<PageResult<TicketSummary>> => {
    const res = await axios.get(`${BASE}/tickets`, { headers: authHeader(), params: { page, size } });
    return res.data;
};

export const getTicketDetail = async (id: number): Promise<TicketDetail> => {
    const res = await axios.get(`${BASE}/tickets/${id}`, { headers: authHeader() });
    return res.data;
};

export const addUserReply = async (id: number, message: string): Promise<TicketDetail> => {
    const res = await axios.post(`${BASE}/tickets/${id}/replies`, { message }, { headers: authHeader() });
    return res.data;
};

export const adminGetTickets = async (status?: string, page = 0, size = 20): Promise<PageResult<TicketSummary>> => {
    const res = await axios.get(`${ADMIN_BASE}/tickets`, { headers: authHeader(), params: { status, page, size } });
    return res.data;
};

export const adminGetTicketDetail = async (id: number): Promise<TicketDetail> => {
    const res = await axios.get(`${ADMIN_BASE}/tickets/${id}`, { headers: authHeader() });
    return res.data;
};

export const adminAddReply = async (id: number, message: string): Promise<TicketDetail> => {
    const res = await axios.post(`${ADMIN_BASE}/tickets/${id}/replies`, { message }, { headers: authHeader() });
    return res.data;
};

export const adminUpdateStatus = async (id: number, status: string): Promise<TicketDetail> => {
    const res = await axios.put(`${ADMIN_BASE}/tickets/${id}/status`, { status }, { headers: authHeader() });
    return res.data;
};

export const adminGetTicketCounts = async (): Promise<Record<string, number>> => {
    const res = await axios.get(`${ADMIN_BASE}/tickets/counts`, { headers: authHeader() });
    return res.data;
};
