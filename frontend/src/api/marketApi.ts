import axios from 'axios';

const BASE_URL = 'http://localhost:9000/api/v1/market';

const authHeader = () => ({
    Authorization: `Bearer ${localStorage.getItem('token')}`,
});

export interface QuoteResponse {
    symbol: string;
    price: number;
    open: number;
    high: number;
    low: number;
    volume: number;
    previousClose: number;
    change: number;
    changePercent: string;
    tradingDay: string;
    fetchedAt: string;
}

export const getQuote = async (symbol: string): Promise<QuoteResponse> => {
    const response = await axios.get(`${BASE_URL}/quote/${symbol}`, { headers: authHeader() });
    return response.data;
};

export const getQuoteHistory = async (symbol: string): Promise<QuoteResponse[]> => {
    const response = await axios.get(`${BASE_URL}/history/${symbol}`, { headers: authHeader() });
    return response.data;
};
