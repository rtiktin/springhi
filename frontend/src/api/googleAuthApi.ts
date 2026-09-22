import API_GATEWAY, { axiosInstance as axios } from './apiBase';

const BASE_URL = `${API_GATEWAY}/api/v1`;

export interface GoogleAuthResponse {
    token: string;
}

export const exchangeGoogleCode = async (
    code: string,
    referralCode?: string,
    adCode?: string,
): Promise<GoogleAuthResponse> => {
    const response = await axios.post(`${BASE_URL}/auth/google`, {
        code,
        referralCode: referralCode || undefined,
        adCode: adCode || undefined,
    });
    return response.data;
};
