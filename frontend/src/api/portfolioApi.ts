import axios from 'axios';

import API_GATEWAY from './apiBase';
const BASE_URL = `${API_GATEWAY}/api/v1/portfolio`;
const PORTFOLIOS_URL = `${API_GATEWAY}/api/v1/portfolios`;

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface Portfolio {
    id: number;
    userId: number;
    name: string;
    description?: string;
    createdAt: string;
    updatedAt: string;
}

export interface AssetWithPrice {
    id: number;
    symbol: string;
    companyName: string | null;
    assetType: string;
    quantity: number;
    averagePrice: number;
    currentPrice: number | null;
    marketValue: number | null;
    costBasis: number | null;
    gainLoss: number | null;
    gainLossPercent: number | null;
}

export interface Transaction {
    id: number;
    symbol: string;
    type: 'BUY' | 'SELL';
    quantity: number;
    price: number;
    timestamp: string;
    recommendationId: number | null;
    aiRunGeneratedAt: string | null;
}

export interface AiRunRec {
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

export interface AiRunDetails {
    recommendations: AiRunRec[];
    profile: PortfolioProfile | null;
}

export interface TransactionRequest {
    symbol: string;
    type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAWAL';
    quantity: number;
    price: number;
}

export const listPortfolios = async (): Promise<Portfolio[]> => {
    const response = await axios.get(PORTFOLIOS_URL, { headers: authHeader() });
    return response.data;
};

export const createPortfolio = async (name: string, description?: string): Promise<Portfolio> => {
    const response = await axios.post(PORTFOLIOS_URL, { name, description }, { headers: authHeader() });
    return response.data;
};

export const updatePortfolio = async (id: number, name: string, description?: string): Promise<Portfolio> => {
    const response = await axios.put(`${PORTFOLIOS_URL}/${id}`, { name, description }, { headers: authHeader() });
    return response.data;
};

export const deletePortfolio = async (id: number): Promise<void> => {
    await axios.delete(`${PORTFOLIOS_URL}/${id}`, { headers: authHeader() });
};

export const getOrCreateDefaultPortfolio = async (): Promise<Portfolio> => {
    const response = await axios.get(`${BASE_URL}/default-portfolio`, { headers: authHeader() });
    return response.data;
};

export const getHoldings = async (portfolioId: number): Promise<AssetWithPrice[]> => {
    const response = await axios.get(BASE_URL, { headers: authHeader(), params: { portfolioId } });
    return response.data;
};

export const getTransactions = async (portfolioId: number): Promise<Transaction[]> => {
    const response = await axios.get(`${BASE_URL}/transactions`, { headers: authHeader(), params: { portfolioId } });
    return response.data;
};

export const submitTransaction = async (req: TransactionRequest, portfolioId: number): Promise<Transaction> => {
    const response = await axios.post(`${BASE_URL}/transactions`, req, {
        headers: authHeader(),
        params: { portfolioId },
    });
    return response.data;
};

export interface PortfolioSnapshot {
    id: number;
    userId: number;
    portfolioId: number;
    snapshotDate: string;
    totalValue: number;
    cashValue: number | null;
    investedValue: number | null;
}

export const getCashBalance = async (portfolioId: number): Promise<number> => {
    const response = await axios.get(`${BASE_URL}/cash`, { headers: authHeader(), params: { portfolioId } });
    return response.data.balance;
};

export const getPortfolioSnapshots = async (portfolioId: number): Promise<PortfolioSnapshot[]> => {
    const response = await axios.get(`${BASE_URL}/snapshots`, { headers: authHeader(), params: { portfolioId } });
    return response.data;
};

export const takePortfolioSnapshot = async (portfolioId: number): Promise<PortfolioSnapshot> => {
    const response = await axios.post(`${BASE_URL}/snapshots`, {}, {
        headers: authHeader(),
        params: { portfolioId },
    });
    return response.data;
};

export const getTodayRecommendations = async (portfolioId: number) => {
    const response = await axios.get(`${BASE_URL}/recommendations`, { headers: authHeader(), params: { portfolioId } });
    return response.data;
};

export const markRecommendationExecuted = async (id: number, transactionId: number, portfolioId: number, actualAmount?: number) => {
    const response = await axios.post(
        `${BASE_URL}/recommendations/${id}/execute`,
        { transactionId, actualAmount },
        { headers: authHeader(), params: { portfolioId } }
    );
    return response.data;
};

export const markRecommendationSkipped = async (id: number, portfolioId: number) => {
    const response = await axios.post(`${BASE_URL}/recommendations/${id}/skip`, {}, {
        headers: authHeader(),
        params: { portfolioId },
    });
    return response.data;
};

export interface PortfolioProfile {
    portfolioId: number;
    riskLevel: string | null;
    goal: string | null;
    horizonYears: number | null;
    liquidityNeeds: string | null;
    additionalComments: string | null;
    currency: string;
    sectorConstraints: string[];
}

export const getPortfolioProfile = async (portfolioId: number): Promise<PortfolioProfile> => {
    const response = await axios.get(`${PORTFOLIOS_URL}/${portfolioId}/profile`, { headers: authHeader() });
    return response.data;
};

export const savePortfolioProfile = async (portfolioId: number, profile: Partial<PortfolioProfile>): Promise<PortfolioProfile> => {
    const response = await axios.put(`${PORTFOLIOS_URL}/${portfolioId}/profile`, profile, { headers: authHeader() });
    return response.data;
};

export const getCompanyName = async (symbol: string, portfolioId: number): Promise<string | null> => {
    const response = await axios.get(`${BASE_URL}/company-name/${symbol}`, {
        headers: authHeader(),
        params: { portfolioId },
    });
    const name = response.data.companyName;
    return name && name.trim() ? name : null;
};

export interface PnlSummary {
    currentMarketValue: number;
    currentCostBasis: number;
    unrealizedPnl: number;
    totalBuyCost: number;
    totalSellProceeds: number;
    realizedPnl: number;
    totalPnl: number;
}

export const getPnlSummary = async (portfolioId: number): Promise<PnlSummary> => {
    const response = await axios.get(`${BASE_URL}/pnl`, {
        headers: authHeader(),
        params: { portfolioId },
    });
    return response.data;
};

export interface TwrSubPeriod {
    startDate: string;
    endDate: string;
    beginValue: number;
    endValue: number;
    netCashFlow: number;
    periodReturnPercent: number;
}

export interface TwrResult {
    twrPercent: number;
    startDate: string;
    endDate: string;
    snapshotCount: number;
    subPeriods: TwrSubPeriod[];
}

export type TwrRange = '1M' | '3M' | '6M' | 'YTD' | '1Y' | 'ALL';

export const getTwr = async (portfolioId: number, range: TwrRange = 'ALL'): Promise<TwrResult> => {
    const response = await axios.get(`${BASE_URL}/performance/twr`, {
        headers: authHeader(),
        params: { portfolioId, range },
    });
    return response.data;
};

export const getAiRunDetails = async (portfolioId: number, generatedAt: string): Promise<AiRunDetails> => {
    const response = await axios.get(`${BASE_URL}/recommendations/run`, {
        headers: authHeader(),
        params: { portfolioId, generatedAt },
    });
    return response.data;
};
