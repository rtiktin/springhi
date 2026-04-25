import axios from 'axios';

import API_GATEWAY from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface UserProfile {
    id?: number;
    userId?: number;
    riskLevel: string;
    goal: string;
    horizonYears: number;
    liquidityNeeds: string;
    knowledgeLevel: string;
    additionalComments: string;
    availableCash: number;
    currency: string;
    sectorConstraints: string[];
}

export interface Recommendation {
    id: number;
    t: string;
    n: string;
    s: string;
    action: 'BUY' | 'SELL';
    w: number;
    r: string;
    status: 'PENDING' | 'EXECUTED' | 'SKIPPED';
    estimatedValue: number | null;
    transactionId: number | null;
    generatedAt: string;
}

export interface OptimizationResult {
    recommendations: Recommendation[];
    error: string | null;
}

export const getProfile = async (): Promise<UserProfile | null> => {
    const response = await axios.get(`${BASE_URL}/profile`, { headers: authHeader() });
    if (response.status === 204) return null;
    return response.data;
};

export const saveProfile = async (profile: UserProfile): Promise<UserProfile> => {
    const response = await axios.put(`${BASE_URL}/profile`, profile, { headers: authHeader() });
    return response.data;
};

export const optimizePortfolio = async (): Promise<OptimizationResult> => {
    const response = await axios.post(`${BASE_URL}/portfolio/optimize`, {}, { headers: authHeader() });
    return response.data;
};
