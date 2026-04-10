import { memo } from 'react';

type BackendStatus = 'checking' | 'online' | 'offline';

type BackendStatusIndicatorProps = {
    status: BackendStatus;
};

const statusStyle: Record<BackendStatus, { dot: string; text: string; label: string }> = {
    checking: {
        dot: 'bg-yellow-400',
        text: 'text-yellow-300',
        label: 'Checking',
    },
    online: {
        dot: 'bg-emerald-400',
        text: 'text-emerald-300',
        label: 'Online',
    },
    offline: {
        dot: 'bg-red-400',
        text: 'text-red-300',
        label: 'Offline',
    },
};

const BackendStatusIndicator = ({ status }: BackendStatusIndicatorProps) => {
    const style = statusStyle[status];

    return (
        <span className="inline-flex items-center gap-2 rounded-full bg-slate-950/50 px-2 py-1 text-xs font-medium">
            <span className={`h-2.5 w-2.5 rounded-full ${style.dot}`} />
            <span className={style.text}>{style.label}</span>
        </span>
    );
};

export default memo(BackendStatusIndicator);

