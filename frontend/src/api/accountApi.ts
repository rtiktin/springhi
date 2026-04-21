import axios from 'axios';

const BASE_URL = 'http://localhost:9000/api/v1/users';

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
}

export const getAccountProfile = async (): Promise<AccountProfile> => {
    const response = await axios.get(`${BASE_URL}/profile`, { headers: authHeader() });
    return response.data;
};

export const updateAccountProfile = async (profile: AccountProfile): Promise<AccountProfile> => {
    const response = await axios.put(`${BASE_URL}/profile`, profile, { headers: authHeader() });
    return response.data;
};
