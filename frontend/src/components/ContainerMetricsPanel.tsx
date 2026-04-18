import { memo, useCallback } from 'react';
import { usePollingStats } from '../hooks/usePollingStats';
import { getContainerStats } from '../util/modpackApi';
import { formatBytes } from '../util/formatBytes';
import MetricBar from './MetricBar';

const POLL_MS = 5000;

type ContainerMetricsPanelProps = {
    packId: number;
    isRunning: boolean;
};

const ContainerMetricsPanel = ({ packId, isRunning }: ContainerMetricsPanelProps) => {
    const fetcher = useCallback(() => getContainerStats(packId), [packId]);
    const stats = usePollingStats(fetcher, POLL_MS, isRunning);

    if (!isRunning || !stats) {
        return null;
    }

    return (
        <div className="mt-4 flex flex-col gap-2 rounded-lg border border-white/5 bg-slate-950/40 p-3">
            <MetricBar label="CPU" percent={stats.cpuPercent} />
            <MetricBar
                label="Memory"
                percent={stats.memoryPercent}
                fraction={{
                    numerator: formatBytes(stats.memoryUsedBytes),
                    denominator: formatBytes(stats.memoryLimitBytes),
                }}
            />
        </div>
    );
};

export default memo(ContainerMetricsPanel);
