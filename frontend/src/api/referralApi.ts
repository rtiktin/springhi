import API_GATEWAY, { axiosInstance as axios } from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/referral`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface ReferralDashboard {
    code: string;
    link: string;
    clicks: number;
    uniqueClicks: number;
    signups: number;
    conversions: number;
    accruedBalance: number;
    paidOut: number;
    active: boolean;
}

export interface ReferralCodeInfo {
    code: string;
    referrerUsername: string;
}

export const getMyReferral = async (): Promise<ReferralDashboard> => {
    const response = await axios.get(`${BASE_URL}/me`, { headers: authHeader() });
    return response.data;
};

export const validateReferralCode = async (code: string): Promise<ReferralCodeInfo | null> => {
    try {
        const response = await axios.get(`${BASE_URL}/code/${code}`);
        return response.data;
    } catch {
        return null;
    }
};

export const recordReferralClick = async (code: string): Promise<void> => {
    try {
        await axios.post(`${BASE_URL}/click`, { code });
    } catch {
        // best-effort; ignore click-recording failures
    }
};

export interface PayoutProfile {
    payableName: string | null;
    payoutEmail: string | null;
    international: boolean;
    entityType: string | null;
    taxIdLast4: string | null;
    hasTaxId: boolean;
    addressLine1: string | null;
    addressLine2: string | null;
    city: string | null;
    state: string | null;
    postalCode: string | null;
    country: string | null;
    readyForPayout: boolean;
    taxInfoComplete: boolean;
}

export type PayoutProfilePayload = Partial<Omit<PayoutProfile, 'taxIdLast4' | 'hasTaxId' | 'readyForPayout' | 'taxInfoComplete'>> & {
    taxId?: string;
};

export const getPayoutProfile = async (): Promise<PayoutProfile> => {
    const response = await axios.get(`${BASE_URL}/payout-profile`, { headers: authHeader() });
    return response.data;
};

export const savePayoutProfile = async (profile: PayoutProfilePayload): Promise<PayoutProfile> => {
    const response = await axios.put(`${BASE_URL}/payout-profile`, profile, { headers: authHeader() });
    return response.data;
};

export const downloadPayoutCsv = async (runId: string): Promise<void> => {
    const token = localStorage.getItem('token');
    const res = await fetch(`${BASE_URL}/admin/payouts/${encodeURIComponent(runId)}/csv`, {
        headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error('Failed to download payout CSV');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `referral-payouts-${runId}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
};
