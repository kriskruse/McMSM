

export type BackendStatus = 'checking' | 'online' | 'offline';
const HEALTH_API_BASE = '/api/health'

export async function healthCheck(path: string): Promise<BackendStatus> {
    const response = await fetch(`${HEALTH_API_BASE}${path}`, { method: 'GET' });
    if (!response.ok) {return 'offline';}
    return 'online';
}