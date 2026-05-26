import type { SettingsResponseDto, SettingsUpdateRequestDto } from '../dto';

const SETTINGS_API = '/api/settings';

export async function getSettings(): Promise<SettingsResponseDto> {
    const response = await fetch(SETTINGS_API);
    if (!response.ok) {
        throw new Error(`Failed to load settings: ${response.status}`);
    }
    return response.json();
}

export async function updateSettings(payload: SettingsUpdateRequestDto): Promise<SettingsResponseDto> {
    const response = await fetch(SETTINGS_API, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        throw new Error(`Failed to update settings: ${response.status}`);
    }
    return response.json();
}
