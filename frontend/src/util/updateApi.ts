import type { UpdateStatusDto } from '../dto';

const UPDATE_API_BASE = '/api/update';

export async function checkForUpdate(): Promise<UpdateStatusDto> {
    const response = await fetch(`${UPDATE_API_BASE}/check`);
    if (!response.ok) {
        throw new Error(`Update check failed: ${response.status}`);
    }
    return response.json();
}

export async function applyUpdate(): Promise<string> {
    const response = await fetch(`${UPDATE_API_BASE}/apply`, { method: 'POST' });
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.error ?? 'Update failed');
    }
    return body.message;
}
