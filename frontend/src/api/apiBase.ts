import axios from 'axios';

const API_GATEWAY = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:9000';

export const axiosInstance = axios.create();

axiosInstance.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 401) {
            const data = error.response?.data;
            if (data?.error === 'TOKEN_EXPIRED' || !localStorage.getItem('token')) {
                localStorage.removeItem('token');
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);

export default API_GATEWAY;
