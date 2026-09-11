import API_GATEWAY, { axiosInstance as axios } from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/ads`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface AdBase {
    id: number;
    ownerUserId: number;
    name: string;
    platform: string;
    externalRef: string | null;
    trackingCode: string;
    status: string;
    notes: string | null;
    createdAt: string | null;
}

export interface AdKpis {
    impressions: number;
    clicks: number;
    spend: number;
    conversions: number;
    revenue: number;
    ctr: number;
    cpc: number;
    cpa: number;
    roas: number;
    roi: number;
}

export interface AdAttribution {
    autoSignups: number;
    autoPaid: number;
    autoRevenue: number;
    autoRoas: number;
}

export type AdComparisonRow = AdBase & AdKpis & AdAttribution;

export interface AdDailyRow extends AdKpis {
    statDate: string;
}

export interface AdDetail extends AdBase, AdKpis, AdAttribution {
    rangeFrom: string;
    rangeTo: string;
    daily: AdDailyRow[];
}

export interface AdPayload {
    name: string;
    platform: string;
    externalRef?: string | null;
    status?: string;
    notes?: string | null;
}

export interface AdStatPayload {
    statDate: string;
    impressions: number;
    clicks: number;
    spend: number;
    conversions: number;
    revenue: number;
}

export const listAds = async (from?: string, to?: string): Promise<AdComparisonRow[]> => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const res = await axios.get(BASE_URL, { headers: authHeader(), params });
    return res.data;
};

export const createAd = async (payload: AdPayload): Promise<AdBase> => {
    const res = await axios.post(BASE_URL, payload, { headers: authHeader() });
    return res.data;
};

export const updateAd = async (id: number, payload: Partial<AdPayload>): Promise<AdBase> => {
    const res = await axios.put(`${BASE_URL}/${id}`, payload, { headers: authHeader() });
    return res.data;
};

export const deleteAd = async (id: number): Promise<void> => {
    await axios.delete(`${BASE_URL}/${id}`, { headers: authHeader() });
};

export const getAdDetail = async (id: number, from?: string, to?: string): Promise<AdDetail> => {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const res = await axios.get(`${BASE_URL}/${id}`, { headers: authHeader(), params });
    return res.data;
};

export const upsertAdStat = async (id: number, payload: AdStatPayload): Promise<unknown> => {
    const res = await axios.put(`${BASE_URL}/${id}/stats`, payload, { headers: authHeader() });
    return res.data;
};

export const deleteAdStat = async (id: number, date: string): Promise<void> => {
    await axios.delete(`${BASE_URL}/${id}/stats/${date}`, { headers: authHeader() });
};
