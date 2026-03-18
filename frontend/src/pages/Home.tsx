import { useCallback, useEffect, useMemo, useState } from 'react';
import BackendStatusIndicator from '../components/BackendStatusIndicator.tsx';
import ModpackCard from '../components/ModpackCard.tsx';
import ModpackExpandedPanel from '../components/ModpackExpandedPanel.tsx';
import ModpackMetadataModal from '../components/ModpackMetadataModal.tsx';
import UploadModpackModal from '../components/UploadModpackModal.tsx';
import type { ModPackCardDto, ModPackMetadataResponseDto, ModPackUploadResponseDto } from '../dto';
import { archivePack, deletePack, deployPack, getAllPacks, startPack, stopPack } from '../util/modpackApi';

const appVersion = import.meta.env.VITE_APP_VERSION ?? '1.0.0';
const apiVersion = import.meta.env.VITE_API_VERSION ?? 'v1';
const DASHBOARD_REFRESH_MS = 15_000;
const BACKEND_RECOVERY_HEALTH_POLL_MS = 5_000;

function isConnectionError(error: unknown): boolean {
    if (error instanceof TypeError) {
        return true;
    }

    if (!(error instanceof Error)) {
        return false;
    }

    const normalizedMessage = error.message.toLowerCase();
    return (
        normalizedMessage.includes('failed to fetch')
        || normalizedMessage.includes('networkerror')
        || normalizedMessage.includes('err_connection')
        || normalizedMessage.includes('load failed')
    );
}

const Home = () => {
    const [modpacks, setModpacks] = useState<ModPackCardDto[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');
    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [isMetadataModalOpen, setIsMetadataModalOpen] = useState(false);
    const [pendingUploadResult, setPendingUploadResult] = useState<ModPackUploadResponseDto | null>(null);
    const [activePackActions, setActivePackActions] = useState<number[]>([]);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [expandedPackId, setExpandedPackId] = useState<number | null>(null);

    const refreshAllPacks = useCallback(async () => {
        try {
            const packs = await getAllPacks();
            setModpacks(packs);
            setBackendStatus('online');
        } catch (error) {
            if (isConnectionError(error)) {
                setBackendStatus('offline');
            } else {
                // A non-connectivity backend error still proves the backend is reachable.
                setBackendStatus('online');
            }
            throw error;
        }
    }, []);

    const checkBackendHealth = useCallback(async () => {
        try {
            const response = await fetch('/api/health');
            if (!response.ok) {
                setBackendStatus('offline');
                return;
            }

            await refreshAllPacks();
            setLoadError('');
        } catch {
            setBackendStatus('offline');
        }
    }, [refreshAllPacks]);

    const handleManualRefresh = useCallback(async () => {
        setIsRefreshing(true);
        setLoadError('');
        try {
            await refreshAllPacks();
        } catch (error) {
            if (error instanceof Error && error.message) {
                setLoadError(error.message);
            } else {
                setLoadError('Failed to refresh modpacks.');
            }
        } finally {
            setIsRefreshing(false);
        }
    }, [refreshAllPacks]);

    useEffect(() => {
        let mounted = true;

        const loadInitialPacks = async () => {
            try {
                await refreshAllPacks();
                if (mounted) {
                    setLoadError('');
                }
            } catch (error) {
                if (mounted) {
                    setLoadError(
                        isConnectionError(error)
                            ? 'Lost connection to backend. Waiting for recovery...'
                            : 'Failed to load modpacks from backend.',
                    );
                }
            } finally {
                if (mounted) {
                    setIsLoading(false);
                }
            }
        };

        void loadInitialPacks();

        return () => {
            mounted = false;
        };
    }, [refreshAllPacks]);

    useEffect(() => {
        if (backendStatus === 'checking') {
            return;
        }

        if (backendStatus === 'online') {
            const refreshTimer = window.setInterval(() => {
                void refreshAllPacks().catch(() => {
                    // Connectivity transitions are handled in refreshAllPacks.
                });
            }, DASHBOARD_REFRESH_MS);

            return () => {
                window.clearInterval(refreshTimer);
            };
        }

        const healthTimer = window.setInterval(() => {
            void checkBackendHealth();
        }, BACKEND_RECOVERY_HEALTH_POLL_MS);

        return () => {
            window.clearInterval(healthTimer);
        };
    }, [backendStatus, checkBackendHealth, refreshAllPacks]);

    const deployedModpacks = useMemo(() => modpacks.filter((pack) => pack.isDeployed), [modpacks]);
    const nonDeployedModpacks = useMemo(() => modpacks.filter((pack) => !pack.isDeployed), [modpacks]);
    const expandedPack = useMemo(
        () => modpacks.find((pack) => pack.packId === expandedPackId) ?? null,
        [modpacks, expandedPackId],
    );

    const setPackActionState = (packId: number, isActive: boolean) => {
        setActivePackActions((previous) => {
            if (isActive) {
                if (previous.includes(packId)) {
                    return previous;
                }
                return [...previous, packId];
            }

            return previous.filter((id) => id !== packId);
        });
    };

    const toggleExpandedPack = (packId: number) => {
        setExpandedPackId((previous) => (previous === packId ? null : packId));
    };

    const handleInlineMetadataSaved = () => {
        void refreshAllPacks();
    };

    const runPackAction = async (packId: number, action: () => Promise<void>) => {
        setPackActionState(packId, true);
        setLoadError('');

        try {
            await action();
            await refreshAllPacks();
        } catch (error) {
            if (error instanceof Error && error.message) {
                setLoadError(error.message);
            } else {
                setLoadError('Failed to update modpack state.');
            }
        } finally {
            setPackActionState(packId, false);
        }
    };

    const handleDeletePack = (packId: number) => {
        const targetPack = modpacks.find((pack) => pack.packId === packId);
        const confirmed = window.confirm(`Delete ${targetPack?.name ?? 'this modpack'}? This removes files and metadata.`);
        if (!confirmed) {
            return;
        }
        void runPackAction(packId, () => deletePack(packId));
    };

    const handleDeployPack = (packId: number) => {
        void runPackAction(packId, () => deployPack(packId));
    };

    const handleStartPack = (packId: number) => {
        void runPackAction(packId, () => startPack(packId));
    };

    const handleStopPack = (packId: number) => {
        void runPackAction(packId, () => stopPack(packId));
    };

    const handleArchivePack = (packId: number) => {
        const targetPack = modpacks.find((pack) => pack.packId === packId);
        const confirmed = window.confirm(`Archive ${targetPack?.name ?? 'this modpack'}? This removes only the container.`);
        if (!confirmed) {
            return;
        }
        void runPackAction(packId, () => archivePack(packId));
    };

    const handleUploadCompleted = (uploadResult: ModPackUploadResponseDto) => {
        setPendingUploadResult(uploadResult);
        setIsUploadModalOpen(false);
        setIsMetadataModalOpen(true);
    };

    const handleMetadataSaved = (response: ModPackMetadataResponseDto) => {
        setIsMetadataModalOpen(false);
        setPendingUploadResult(null);
        if (response.message.toLowerCase().includes('failed')) {
            setLoadError(response.message);
        } else {
            setLoadError('');
        }
        void refreshAllPacks();
    };

    return (
        <main className="w-full max-w-6xl px-4 py-6 text-slate-100 md:px-6">
            <header className="mb-6 flex flex-col gap-4 rounded-2xl border border-white/10 bg-slate-900/75 p-5 md:flex-row md:items-center md:justify-between">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-white md:text-3xl">
                        MC Modded Server Manager
                    </h1>
                    <p className="mt-1 text-sm text-slate-400">
                        <span className="text-xs">By</span> Kris Kruse
                    </p>
                </div>

                <div className="w-fit rounded-xl border border-white/10 bg-slate-800/70 px-4 py-3 text-sm text-slate-300">
                    <h3 className="text-lg font-bold leading-none text-white">Backend</h3>
                    <div className="mt-2 flex items-center gap-2">
                        <span className="inline-flex items-center rounded-full bg-slate-950/50 px-2 py-1 text-xs font-medium text-slate-300">
                            {apiVersion}
                        </span>
                        <BackendStatusIndicator status={backendStatus} />
                    </div>
                </div>
            </header>

            <div className="mb-4 flex justify-end gap-2">
                <button
                    type="button"
                    onClick={() => {
                        void handleManualRefresh();
                    }}
                    className="inline-flex items-center gap-2 rounded-lg border border-white/20 bg-slate-800 px-4 py-2 text-sm font-semibold text-white transition hover:bg-slate-700 disabled:opacity-50"
                    aria-label="Refresh modpacks"
                    disabled={isRefreshing || isLoading}
                >
                    {isRefreshing ? 'Refreshing...' : 'Refresh'}
                </button>
                <button
                    type="button"
                    onClick={() => setIsUploadModalOpen(true)}
                    className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-500"
                    aria-label="Upload new modpack"
                >
                    <span className="text-lg leading-none">+</span>
                    Upload Modpack
                </button>
            </div>

            <section className="mb-6 rounded-2xl border border-white/10 bg-slate-900/70 p-5">
                <h2 className="mb-4 text-lg font-semibold text-white">Currently Deployed Modpacks</h2>
                {isLoading && <p className="text-sm text-slate-400">Loading modpacks...</p>}
                {!isLoading && loadError && <p className="text-sm text-red-400">{loadError}</p>}
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {deployedModpacks.map((pack) => (
                        <ModpackCard
                            key={pack.packId}
                            modpack={pack}
                            isBusy={activePackActions.includes(pack.packId)}
                            isExpanded={expandedPackId === pack.packId}
                            onToggleExpand={toggleExpandedPack}
                            onDelete={handleDeletePack}
                            onDeploy={handleDeployPack}
                            onArchive={handleArchivePack}
                            onStart={handleStartPack}
                            onStop={handleStopPack}
                        />
                    ))}
                </div>
            </section>

            <section className="rounded-2xl border border-white/10 bg-slate-900/70 p-5">
                <h2 className="mb-4 text-lg font-semibold text-white">Saved Instances</h2>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {nonDeployedModpacks.map((pack) => (
                        <ModpackCard
                            key={pack.packId}
                            modpack={pack}
                            isBusy={activePackActions.includes(pack.packId)}
                            isExpanded={expandedPackId === pack.packId}
                            onToggleExpand={toggleExpandedPack}
                            onDelete={handleDeletePack}
                            onDeploy={handleDeployPack}
                            onArchive={handleArchivePack}
                            onStart={handleStartPack}
                            onStop={handleStopPack}
                        />
                    ))}
                </div>
            </section>

            <UploadModpackModal
                isOpen={isUploadModalOpen}
                onClose={() => setIsUploadModalOpen(false)}
                onUploaded={handleUploadCompleted}
            />

            <ModpackMetadataModal
                isOpen={isMetadataModalOpen}
                uploadResult={pendingUploadResult}
                onClose={() => {
                    setIsMetadataModalOpen(false);
                    setPendingUploadResult(null);
                }}
                onSaved={handleMetadataSaved}
            />

            {expandedPack && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4 py-4"
                    onClick={() => setExpandedPackId(null)}
                >
                    <div
                        className="relative w-full max-w-7xl max-h-[92vh] overflow-auto rounded-2xl border border-white/10 bg-slate-900 p-8 shadow-2xl"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <button
                            type="button"
                            onClick={() => setExpandedPackId(null)}
                            className="absolute right-4 top-4 inline-flex h-9 w-9 items-center justify-center rounded-full border border-white/25 bg-slate-800 text-xl leading-none text-slate-200 transition hover:bg-slate-700 hover:text-white"
                            aria-label="Close modpack details"
                        >
                            <span className="translate-y-[-1px]">x</span>
                        </button>
                        <ModpackExpandedPanel
                            modpack={expandedPack}
                            onMetadataSaved={handleInlineMetadataSaved}
                        />
                    </div>
                </div>
            )}

            <div className="fixed bottom-3 right-4 text-xs text-slate-500">
                {appVersion}
            </div>
        </main>
    );
};

export default Home;