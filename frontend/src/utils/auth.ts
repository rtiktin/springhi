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
