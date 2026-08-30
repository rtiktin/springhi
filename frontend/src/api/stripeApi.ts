import API_GATEWAY, { axiosInstance as axios } from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/subscription`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface StripePublicConfig {
    enabled: boolean;
    publishableKey: string;
}

export const getStripeConfig = async (): Promise<StripePublicConfig> => {
    const res = await axios.get(`${BASE_URL}/stripe-config`);
    return res.data;
};

export const createSetupIntent = async (): Promise<string> => {
    const res = await axios.post(`${BASE_URL}/setup-intent`, {}, { headers: authHeader() });
    return res.data.clientSecret;
};

export const confirmPaymentMethod = async (paymentMethodId: string): Promise<unknown> => {
    const res = await axios.post(`${BASE_URL}/confirm-payment-method`, { paymentMethodId }, { headers: authHeader() });
    return res.data;
};

export interface TestSubscribeResult {
    stripeSubscriptionId: string;
    stripeCustomerId: string;
    stripeTestClockId: string;
    failPayment: boolean;
    status: string;
}

export const adminTestSubscribe = async (userId: number, planName: string, billingCycle: string, failPayment: boolean): Promise<TestSubscribeResult> => {
    const res = await axios.post(`${BASE_URL}/test/subscribe`, { userId, planName, billingCycle, failPayment }, { headers: authHeader() });
    return res.data;
};

export const adminCreateTestClock = async (frozenTime?: number): Promise<{ testClockId: string; frozenTime: number }> => {
    const body = frozenTime != null ? { frozenTime } : {};
    const res = await axios.post(`${BASE_URL}/test/clock`, body, { headers: authHeader() });
    return res.data;
};

export const adminAdvanceTestClock = async (testClockId: string, frozenTime: number): Promise<{ testClockId: string; frozenTime: number; note: string }> => {
    const res = await axios.post(`${BASE_URL}/test/clock/${encodeURIComponent(testClockId)}/advance`, { frozenTime }, { headers: authHeader() });
    return res.data;
};

export interface StripeWebhookStatus {
    lastType: string | null;
    lastEventId: string | null;
    lastEventAt: string | null;
    totalEvents: number;
    enabled: boolean;
    configured: boolean;
}

export const getStripeWebhookStatus = async (): Promise<StripeWebhookStatus> => {
    const res = await axios.get(`${BASE_URL}/webhook/last`, { headers: authHeader() });
    return res.data;
};
