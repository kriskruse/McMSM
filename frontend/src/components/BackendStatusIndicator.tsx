import { memo } from 'react';
import type { BackendStatus } from '../util/healthCheck';
import { resolveBackendIndicator } from '../util/statusIndicator';
import StatusBadge from './StatusBadge';

type BackendStatusIndicatorProps = {
    status: BackendStatus;
};

const BackendStatusIndicator = ({ status }: BackendStatusIndicatorProps) => (
    <StatusBadge indicator={resolveBackendIndicator(status)} />
);

export default memo(BackendStatusIndicator);
