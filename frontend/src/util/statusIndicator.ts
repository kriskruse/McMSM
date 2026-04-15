import type { ModPackCardDto } from '../dto';

export const statusStyles = {
    green: {
        dot: 'bg-emerald-400',
        text: 'text-emerald-300',
        label: 'Running',
    },
    blue: {
        dot: 'bg-blue-400',
        text: 'text-blue-300',
        label: 'Stopped',
    },
    red: {
        dot: 'bg-red-400',
        text: 'text-red-300',
        label: 'Error',
    },
    yellow: {
        dot: 'bg-yellow-400',
        text: 'text-yellow-300',
        label: 'Pending',
    },
    slate: {
        dot: 'bg-slate-400',
        text: 'text-slate-300',
        label: 'Saved',
    },
} as const;

export type StatusIndicator = (typeof statusStyles)[keyof typeof statusStyles];

export function resolveIndicator(modpack: ModPackCardDto): StatusIndicator {
    const status = modpack.status.toLowerCase();

    if (status.includes('error') || status.includes('failed')) {
        return statusStyles.red;
    }

    if (!modpack.isDeployed || status === 'saved' || status === 'not_deployed') {
        return statusStyles.slate;
    }

    if (modpack.isDeployed && (status === 'running' || status === 'deployed')) {
        return statusStyles.green;
    }

    if (status === 'stopped') {
        return statusStyles.blue;
    }

    return statusStyles.yellow;
}
