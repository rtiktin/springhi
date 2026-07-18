import API_GATEWAY, { axiosInstance as axios } from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/users`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface AccountProfile {
    id?: number;
    username?: string;
    email?: string;
    firstName: string;
    lastName: string;
    bio: string;
    phone: string;
    addressLine1: string;
    addressLine2: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    dateOfBirth: string;
    createdAt?: string;
    updatedAt?: string;
    emailVerified?: boolean;
    phoneVerified?: boolean;
}

export const getAccountProfile = async (): Promise<AccountProfile> => {
    const response = await axios.get(`${BASE_URL}/profile`, { headers: authHeader() });
    return response.data;
};

export const updateAccountProfile = async (profile: AccountProfile): Promise<AccountProfile> => {
    const response = await axios.put(`${BASE_URL}/profile`, profile, { headers: authHeader() });
    return response.data;
};

export const sendEmailVerification = async (email?: string): Promise<{ token: string }> => {
    const response = await axios.post(`${BASE_URL}/email/send-verification`, { email: email ?? '' }, { headers: authHeader() });
    return response.data;
};

export const verifyEmail = async (code: string): Promise<{ token: string }> => {
    const response = await axios.post(`${BASE_URL}/email/verify`, { code }, { headers: authHeader() });
    return response.data;
};

export const sendPhoneVerification = async (phone?: string): Promise<{ token: string }> => {
    const response = await axios.post(`${BASE_URL}/phone/send-verification`, { phone: phone ?? '' }, { headers: authHeader() });
    return response.data;
};

export const verifyPhone = async (code: string): Promise<{ token: string }> => {
    const response = await axios.post(`${BASE_URL}/phone/verify`, { code }, { headers: authHeader() });
    return response.data;
};
