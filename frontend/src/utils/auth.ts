export function getLoggedInUsername(): string | null {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.sub ?? null;
    } catch {
        return null;
    }
}

export function getUserType(): number {
    const token = localStorage.getItem('token');
    if (!token) return 0;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.userType ?? 8;
    } catch {
        return 8;
    }
}

export function isAdmin(): boolean {
    return getUserType() === 10;
}

export function isImpersonating(): boolean {
    return !!localStorage.getItem('adminToken');
}

export function startImpersonation(userToken: string): void {
    const adminToken = localStorage.getItem('token');
    if (adminToken) {
        localStorage.setItem('adminToken', adminToken);
    }
    localStorage.setItem('token', userToken);
}

export function stopImpersonation(): void {
    const adminToken = localStorage.getItem('adminToken');
    if (adminToken) {
        localStorage.setItem('token', adminToken);
        localStorage.removeItem('adminToken');
    }
}

export function isLoggedIn(): boolean {
    const token = localStorage.getItem('token');
    if (!token) return false;
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const exp = payload.exp;
        if (exp && Date.now() / 1000 > exp) {
            localStorage.removeItem('token');
            return false;
        }
        return true;
    } catch {
        return false;
    }
}
