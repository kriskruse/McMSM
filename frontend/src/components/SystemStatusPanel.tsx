import { memo } from 'react';
import type { BackendStatus } from '../util/healthCheck';
import { usePollingStats } from '../hooks/usePollingStats';
import { getSystemStats } from '../util/modpackApi';
import { formatBytes } from '../util/formatBytes';
import BackendStatusIndicator from './BackendStatusIndicator';
import MetricBar from './MetricBar';

const POLL_MS = 5000;

type SystemStatusPanelProps = {
    backendStatus: BackendStatus;
    dockerStatus: BackendStatus;
};

const StatusChip = ({ label, status }: { label: string; status: BackendStatus }) => (
    <div className="flex items-center gap-1.5">
        <span className="text-[10px] font-medium uppercase tracking-wide text-slate-400">{label}</span>
        <BackendStatusIndicator status={status} />
    </div>
);

const SystemStatusPanel = ({ backendStatus, dockerStatus }: SystemStatusPanelProps) => {
    const stats = usePollingStats(getSystemStats, POLL_MS, backendStatus === 'online');

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center justify-end gap-6">
                <StatusChip label="Backend" status={backendStatus} />
                <StatusChip label="Docker" status={dockerStatus} />
            </div>
            {!stats ? (
                <p className="text-right text-[11px] text-slate-500">
                    {backendStatus === 'online' ? 'Waiting for host stats...' : 'Metrics offline'}
                </p>
            ) : (
                <div className="grid grid-cols-3 gap-8">
                    <MetricBar label="CPU" percent={stats.cpuPercent} />
                    <MetricBar
                        label="Memory"
                        percent={stats.memoryPercent}
                        fraction={{
                            numerator: formatBytes(stats.memoryUsedBytes),
                            denominator: formatBytes(stats.memoryTotalBytes),
                        }}
                    />
                    <MetricBar
                        label="Disk"
                        percent={stats.diskPercent}
                        fraction={{
                            numerator: formatBytes(stats.diskUsedBytes),
                            denominator: formatBytes(stats.diskTotalBytes),
                        }}
                    />
                </div>
            )}
        </div>
    );
};

export default memo(SystemStatusPanel);
