import API_GATEWAY, { axiosInstance as axios } from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/subscription`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface StripePublicConfig {
    enabled: boolean;
    publishableKey: string;
    linkEnabled: boolean;
    liveMode: boolean;
}

export const getStripeConfig = async (): Promise<StripePublicConfig> => {
    const res = await axios.get(`${BASE_URL}/stripe-config`);
    return res.data;
};

export interface StripeLinkSetting {
    liveMode: boolean;
    linkEnabled: boolean;
}

/** Admin-only: read the effective Stripe Link toggle + live-mode flag. */
export const getStripeLinkSetting = async (): Promise<StripeLinkSetting> => {
    const res = await axios.get(`${BASE_URL}/stripe-link`, { headers: authHeader() });
    return res.data;
};

/** Admin-only: turn Stripe Link on/off (test and live mode). */
export const setStripeLinkSetting = async (enabled: boolean): Promise<StripeLinkSetting> => {
    const res = await axios.post(`${BASE_URL}/stripe-link`, { enabled }, { headers: authHeader() });
    return res.data;
};

export interface SetupIntentBillingDetails {
    name?: string;
    email?: string;
    phone?: string;
}

export interface SetupIntentResult {
    clientSecret: string;
    /** Account-derived defaults used to prefill the PaymentElement's billing details. */
    billingDetails: SetupIntentBillingDetails;
}

export const createSetupIntent = async (): Promise<SetupIntentResult> => {
    const res = await axios.post(`${BASE_URL}/setup-intent`, {}, { headers: authHeader() });
    return { clientSecret: res.data.clientSecret, billingDetails: res.data.billingDetails ?? {} };
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

export interface SandboxCleanupResult {
    usersRemoved?: number;
    stripeSubscriptionsCanceled?: number;
    stripeCustomersDeleted?: number;
    stripeTestClocksDeleted?: number;
    stripePaymentMethodsDetached?: number;
    dbSubscriptionRowsDeleted?: number;
    dbOrphanedSubscriptionRowsDeleted?: number;
    dbPaymentMethodRowsDeleted?: number;
    dbPaymentHistoryRowsDeleted?: number;
    errors?: string[];
    message?: string;
}

/** Admin-only: delete Stripe + DB rows created by StripeSandboxIT / the test-subscribe flow. */
export const adminCleanupSandbox = async (): Promise<SandboxCleanupResult> => {
    const res = await axios.post(`${BASE_URL}/test/cleanup`, {}, { headers: authHeader() });
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
