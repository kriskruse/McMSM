import type { ModPackCardDto } from '../dto';
import type { BackendStatus } from './healthCheck';

export type StatusIndicator = { dot: string; text: string; label: string };

const modpackStyles = {
    green: { dot: 'bg-emerald-400', text: 'text-emerald-300', label: 'Running' },
    blue: { dot: 'bg-blue-400', text: 'text-blue-300', label: 'Stopped' },
    red: { dot: 'bg-red-400', text: 'text-red-300', label: 'Error' },
    yellow: { dot: 'bg-yellow-400', text: 'text-yellow-300', label: 'Pending' },
    slate: { dot: 'bg-slate-400', text: 'text-slate-300', label: 'Saved' },
} as const;

const backendStyles: Record<BackendStatus, StatusIndicator> = {
    checking: { dot: 'bg-yellow-400', text: 'text-yellow-300', label: 'Checking' },
    online: { dot: 'bg-emerald-400', text: 'text-emerald-300', label: 'Online' },
    offline: { dot: 'bg-red-400', text: 'text-red-300', label: 'Offline' },
} as const;

export function resolveIndicator(modpack: ModPackCardDto): StatusIndicator {
    const status = modpack.status.toLowerCase();

    if (status.includes('error') || status.includes('failed')) {
        return modpackStyles.red;
    }

    if (!modpack.isDeployed || status === 'saved' || status === 'not_deployed') {
        return modpackStyles.slate;
    }

    if (modpack.isDeployed && (status === 'running' || status === 'deployed')) {
        return modpackStyles.green;
    }

    if (status === 'stopped') {
        return modpackStyles.blue;
    }

    return modpackStyles.yellow;
}

export function resolveBackendIndicator(status: BackendStatus): StatusIndicator {
    return backendStyles[status];
}
