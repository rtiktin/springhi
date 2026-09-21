/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string;
    readonly VITE_GOOGLE_CLIENT_ID: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}

interface GoogleCodeClientConfig {
    client_id: string;
    scope: string;
    ux_mode: 'popup';
    callback: (response: { code: string }) => void;
    error_callback?: (error: unknown) => void;
}

interface GoogleCodeClient {
    requestCode: () => void;
}

interface GoogleOauth2 {
    initCodeClient: (config: GoogleCodeClientConfig) => GoogleCodeClient;
}

interface GoogleAccounts {
    oauth2: GoogleOauth2;
}

interface Window {
    google?: { accounts: GoogleAccounts };
}
