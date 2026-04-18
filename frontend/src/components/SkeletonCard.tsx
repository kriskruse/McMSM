import { memo } from 'react';

const SkeletonCard = () => (
    <article className="animate-pulse rounded-xl border border-white/10 bg-slate-900/70 p-4 shadow-md">
        <div className="mb-3 flex items-start justify-between gap-4">
            <div className="h-5 w-36 rounded bg-slate-700/60" />
            <div className="h-6 w-20 rounded-full bg-slate-700/60" />
        </div>

        <div className="grid grid-cols-2 gap-2">
            {Array.from({ length: 4 }, (_, i) => (
                <div key={i} className="space-y-1.5">
                    <div className="h-3 w-14 rounded bg-slate-700/40" />
                    <div className="h-4 w-20 rounded bg-slate-700/60" />
                </div>
            ))}
        </div>

        <div className="mt-4 flex items-center gap-2">
            <div className="h-8 w-16 rounded-lg bg-slate-700/60" />
            <div className="ml-auto h-8 w-8 rounded-md bg-slate-700/60" />
            <div className="h-8 w-8 rounded-md bg-slate-700/60" />
        </div>

        <div className="mt-4 h-3 w-28 rounded bg-slate-700/40" />
    </article>
);

export default memo(SkeletonCard);
