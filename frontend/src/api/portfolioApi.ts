import axios from 'axios';

const BASE_URL = 'http://localhost:9000/api/v1/portfolio';

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface AssetWithPrice {
    id: number;
    symbol: string;
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
}

export interface TransactionRequest {
    symbol: string;
    type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAWAL';
    quantity: number;
    price: number;
}

export const getHoldings = async (): Promise<AssetWithPrice[]> => {
    const response = await axios.get(BASE_URL, { headers: authHeader() });
    return response.data;
};

export const getTransactions = async (): Promise<Transaction[]> => {
    const response = await axios.get(`${BASE_URL}/transactions`, { headers: authHeader() });
    return response.data;
};

export const submitTransaction = async (req: TransactionRequest): Promise<Transaction> => {
    const response = await axios.post(`${BASE_URL}/transactions`, req, { headers: authHeader() });
    return response.data;
};

export interface PortfolioSnapshot {
    id: number;
    userId: number;
    snapshotDate: string;
    totalValue: number;
    cashValue: number | null;
    investedValue: number | null;
}

export const getCashBalance = async (): Promise<number> => {
    const response = await axios.get(`${BASE_URL}/cash`, { headers: authHeader() });
    return response.data.balance;
};

export const getPortfolioSnapshots = async (): Promise<PortfolioSnapshot[]> => {
    const response = await axios.get(`${BASE_URL}/snapshots`, { headers: authHeader() });
    return response.data;
};

export const takePortfolioSnapshot = async (): Promise<PortfolioSnapshot> => {
    const response = await axios.post(`${BASE_URL}/snapshots`, {}, { headers: authHeader() });
    return response.data;
};

export const getTodayRecommendations = async () => {
    const response = await axios.get(`${BASE_URL}/recommendations`, { headers: authHeader() });
    return response.data;
};

export const markRecommendationExecuted = async (id: number, transactionId: number, actualAmount?: number) => {
    const response = await axios.post(
        `${BASE_URL}/recommendations/${id}/execute`,
        { transactionId, actualAmount },
        { headers: authHeader() }
    );
    return response.data;
};

export const markRecommendationSkipped = async (id: number) => {
    const response = await axios.post(`${BASE_URL}/recommendations/${id}/skip`, {}, { headers: authHeader() });
    return response.data;
};
