import { memo } from 'react';

type MetadataTagProps = {
    label: string;
    value: string;
};

const MetadataTag = ({ label, value }: MetadataTagProps) => (
    <div className="rounded-md border border-white/10 bg-slate-950/50 px-3 py-2 text-xs">
        <p className="text-slate-400">{label}</p>
        <p className="mt-1 break-all font-medium text-slate-200">{value || '-'}</p>
    </div>
);

export default memo(MetadataTag);
