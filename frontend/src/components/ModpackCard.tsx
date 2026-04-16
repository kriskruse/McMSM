import { memo } from 'react';
import type { ModPackCardDto } from '../dto';
import { resolveIndicator } from '../util/statusIndicator';
import StatusBadge from './StatusBadge';

type ModpackCardProps = {
    modpack: ModPackCardDto;
    isBusy?: boolean;
    isExpanded: boolean;
    onToggleExpand: (packId: number) => void;
    onUpdate: (packId: number, packName?: string) => void;
    onDelete: (packId: number) => void;
    onDeploy: (packId: number) => void;
    onArchive: (packId: number) => void;
    onStart: (packId: number) => void;
    onStop: (packId: number) => void;
};

function toLastUpdatedLabel(updatedAt: string | null) {
    if (!updatedAt) {
        return 'Last update: unknown';
    }

    const date = new Date(updatedAt);
    if (Number.isNaN(date.getTime())) {
        return 'Last update: unknown';
    }

    const elapsedMs = Date.now() - date.getTime();
    const minutes = Math.floor(elapsedMs / 60000);

    if (minutes < 1) {
        return 'Last update: just now';
    }
    if (minutes < 60) {
        return `Last update: ${minutes}m ago`;
    }

    const hours = Math.floor(minutes / 60);
    if (hours < 24) {
        return `Last update: ${hours}h ago`;
    }

    const days = Math.floor(hours / 24);
    return `Last update: ${days}d ago`;
}

const ModpackCard = ({
    modpack,
    isBusy = false,
    isExpanded,
    onToggleExpand,
    onUpdate,
    onDelete,
    onDeploy,
    onArchive,
    onStart,
    onStop,
}: ModpackCardProps) => {
    const indicator = resolveIndicator(modpack);
    const isRunning = modpack.status === 'running';
    const startButtonClass = isRunning
        ? 'bg-emerald-800/70 text-emerald-300'
        : 'bg-emerald-600 text-white hover:bg-emerald-500';
    const stopButtonClass = isRunning
        ? 'bg-rose-600 text-white hover:bg-rose-500'
        : 'bg-rose-900/70 text-rose-300';

    return (
        <article className="rounded-xl border border-white/10 bg-slate-900/70 p-4 shadow-md">
            <div className="mb-3 flex items-start justify-between gap-4">
                <h3 className="min-w-0 truncate text-lg font-semibold text-white" title={modpack.name}>{modpack.name}</h3>
                <div className="flex items-center gap-2">
                    <StatusBadge indicator={indicator} />
                    <button
                        type="button"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-white/20 bg-slate-800/80 text-slate-200 transition hover:bg-slate-700"
                        onClick={() => onToggleExpand(modpack.packId)}
                        aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${modpack.name} details`}
                    >
                        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M8 4H4v4" />
                            <path d="M16 4h4v4" />
                            <path d="M4 16v4h4" />
                            <path d="M20 16v4h-4" />
                        </svg>
                    </button>
                </div>
            </div>

            <dl className="grid grid-cols-2 gap-2 text-sm text-slate-200">
                <div>
                    <dt className="text-slate-400">Pack</dt>
                    <dd>{modpack.packVersion}</dd>
                </div>
                <div>
                    <dt className="text-slate-400">Minecraft</dt>
                    <dd>{modpack.minecraftVersion}</dd>
                </div>
                <div>
                    <dt className="text-slate-400">Port</dt>
                    <dd>{modpack.port}</dd>
                </div>
                <div>
                    <dt className="text-slate-400">Java</dt>
                    <dd>{modpack.javaVersion}</dd>
                </div>
                {modpack.loaderType && modpack.loaderType !== 'unknown' && (
                    <div>
                        <dt className="text-slate-400">Loader</dt>
                        <dd className="capitalize">{modpack.loaderType}</dd>
                    </div>
                )}
            </dl>

            <div className="mt-4 flex items-center gap-2">
                {!modpack.isDeployed && (
                    <button
                        type="button"
                        className="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-50"
                        onClick={() => onDeploy(modpack.packId)}
                        disabled={isBusy}
                        aria-label={`Deploy ${modpack.name}`}
                    >
                        Deploy
                    </button>
                )}

                {modpack.isDeployed && (
                    <>
                        <button
                            type="button"
                            className={`inline-flex h-10 w-10 items-center justify-center rounded-md transition disabled:opacity-50 ${startButtonClass}`}
                            onClick={() => onStart(modpack.packId)}
                            disabled={isBusy || isRunning}
                            aria-label={`Start ${modpack.name}`}
                        >
                            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                <path d="M8 5v14l11-7z" />
                            </svg>
                        </button>
                        <button
                            type="button"
                            className={`inline-flex h-10 w-10 items-center justify-center rounded-md transition disabled:opacity-50 ${stopButtonClass}`}
                            onClick={() => onStop(modpack.packId)}
                            disabled={isBusy || !isRunning}
                            aria-label={`Stop ${modpack.name}`}
                        >
                            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                                <rect x="7" y="7" width="10" height="10" rx="1" />
                            </svg>
                        </button>
                    </>
                )}
                {modpack.isDeployed && (
                    <button
                        type="button"
                        className="ml-auto inline-flex h-8 w-8 items-center justify-center rounded-md bg-amber-600 text-white transition hover:bg-amber-500 disabled:opacity-50"
                        onClick={() => onArchive(modpack.packId)}
                        disabled={isBusy}
                        aria-label={`Archive ${modpack.name}`}
                    >
                        <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                            <path d="M3 7h18" />
                            <path d="M5 7v12h14V7" />
                            <path d="M9 11h6" />
                            <path d="M4 4h16v3H4z" />
                        </svg>
                    </button>
                )}
                <button
                    type="button"
                    className={`${modpack.isDeployed ? '' : 'ml-auto'} inline-flex h-8 w-8 items-center justify-center rounded-md bg-slate-600 text-white transition hover:bg-slate-500 disabled:opacity-50`}
                    onClick={() => onUpdate(modpack.packId, modpack.name)}
                    disabled={isBusy}
                    aria-label={`Update ${modpack.name}`}
                >
                    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                        <path d="M12 3v12" />
                        <path d="m7 10 5 5 5-5" />
                        <path d="M4 18h16" />
                    </svg>
                </button>
                <button
                    type="button"
                    className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-red-600 text-white transition hover:bg-red-500 disabled:opacity-50"
                    onClick={() => onDelete(modpack.packId)}
                    disabled={isBusy}
                    aria-label={`Delete ${modpack.name}`}
                >
                    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                        <path d="M3 6h18" />
                        <path d="M8 6V4h8v2" />
                        <path d="M19 6l-1 14H6L5 6" />
                        <path d="M10 11v6" />
                        <path d="M14 11v6" />
                    </svg>
                </button>
            </div>

            <p className="mt-4 text-xs text-slate-400">{toLastUpdatedLabel(modpack.updatedAt)}</p>
        </article>
    );
};

export default memo(ModpackCard);

