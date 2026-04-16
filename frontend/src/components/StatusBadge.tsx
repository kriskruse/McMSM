import { memo } from 'react';
import type { StatusIndicator } from '../util/statusIndicator';

type StatusBadgeProps = {
    indicator: StatusIndicator;
};

const StatusBadge = ({ indicator }: StatusBadgeProps) => (
    <span className="inline-flex items-center gap-2 rounded-full bg-slate-950/60 px-2.5 py-0.5 text-xs font-medium">
        <span className={`h-2.5 w-2.5 rounded-full ${indicator.dot}`} />
        <span className={indicator.text}>{indicator.label}</span>
    </span>
);

export default memo(StatusBadge);
