import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import BackendStatusIndicator from '../components/BackendStatusIndicator.tsx';
import ModpackCard from '../components/ModpackCard.tsx';
import ModpackConsole from '../components/ModpackConsole.tsx';
import ModpackMetadataModal from '../components/ModpackMetadataModal.tsx';
import UploadModpackModal from '../components/UploadModpackModal.tsx';
import type { ModPackCardDto, ModPackMetadataResponseDto, ModPackUploadResponseDto } from '../dto';
import { archivePack, deletePack, deployPack, getAllPacks, startPack, stopPack, updateModpack } from '../util/modpackApi';

const appVersion = import.meta.env.VITE_APP_VERSION ?? '1.0.0';
const apiVersion = import.meta.env.VITE_API_VERSION ?? 'v1';
const DASHBOARD_REFRESH_MS = 15_000;
const BACKEND_RECOVERY_HEALTH_POLL_MS = 5_000;

function isZipFile(file: File): boolean {
    return file.name.toLowerCase().endsWith('.zip');
}

function isFileDragEvent(event: DragEvent): boolean {
    return event.dataTransfer?.types?.includes('Files') ?? false;
}

function getDroppedFile(event: DragEvent): File | null {
    return event.dataTransfer?.files?.[0] ?? null;
}

function toUploadResultFromPack(pack: ModPackCardDto): ModPackUploadResponseDto {
    return {
        packId: pack.packId,
        name: pack.name,
        path: pack.path,
        packVersion: pack.packVersion,
        minecraftVersion: pack.minecraftVersion,
        javaVersion: pack.javaVersion,
        javaXmx: pack.javaXmx,
        port: pack.port,
        entryPoint: pack.entryPoint,
        entryPointCandidates: pack.entryPointCandidates,
        message: '',
    };
}

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
    const [pendingUploadFile, setPendingUploadFile] = useState<File | null>(null);
    const [uploadMode, setUploadMode] = useState<'upload' | 'update'>('upload');
    const [updateTargetPackId, setUpdateTargetPackId] = useState<number | null>(null);
    const [isGlobalFileDragActive, setIsGlobalFileDragActive] = useState(false);
    const globalFileDragDepth = useRef(0);

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
        const targetPack = modpacks.find((pack) => pack.packId === packId);
        if (!targetPack) {
            return;
        }

        if (!targetPack.isDeployed) {
            setExpandedPackId(null);
            setPendingUploadResult(toUploadResultFromPack(targetPack));
            setIsMetadataModalOpen(true);
            return;
        }

        setExpandedPackId((previous) => (previous === packId ? null : packId));
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
        setPendingUploadFile(null);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
        setPendingUploadResult(uploadResult);
        setIsUploadModalOpen(false);
        setIsMetadataModalOpen(true);
    };

    const handleCloseUploadModal = () => {
        setIsUploadModalOpen(false);
        setPendingUploadFile(null);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
    };

    const handleUpdatePack = (packId: number) => {
        setLoadError('');
        setPendingUploadFile(null);
        setUploadMode('update');
        setUpdateTargetPackId(packId);
        setIsUploadModalOpen(true);
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

    const handleGlobalDragOver = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'copy';
        }
    }, []);

    const handleGlobalDragEnter = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        globalFileDragDepth.current += 1;
        setIsGlobalFileDragActive(true);
    }, []);

    const handleGlobalDragLeave = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        globalFileDragDepth.current = Math.max(0, globalFileDragDepth.current - 1);
        if (globalFileDragDepth.current === 0) {
            setIsGlobalFileDragActive(false);
        }
    }, []);

    const handleGlobalDrop = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        globalFileDragDepth.current = 0;
        setIsGlobalFileDragActive(false);

        const droppedFile = getDroppedFile(event);
        if (!droppedFile) {
            return;
        }

        if (!isZipFile(droppedFile)) {
            setLoadError('Only .zip files are supported for upload.');
            return;
        }

        setLoadError('');
        setPendingUploadFile(droppedFile);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
        setIsUploadModalOpen(true);
    }, []);

    useEffect(() => {
        window.addEventListener('dragenter', handleGlobalDragEnter);
        window.addEventListener('dragover', handleGlobalDragOver);
        window.addEventListener('dragleave', handleGlobalDragLeave);
        window.addEventListener('drop', handleGlobalDrop);

        return () => {
            window.removeEventListener('dragenter', handleGlobalDragEnter);
            window.removeEventListener('dragover', handleGlobalDragOver);
            window.removeEventListener('dragleave', handleGlobalDragLeave);
            window.removeEventListener('drop', handleGlobalDrop);
        };
    }, [handleGlobalDragEnter, handleGlobalDragLeave, handleGlobalDragOver, handleGlobalDrop]);

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
                    onClick={() => {
                        setPendingUploadFile(null);
                        setUploadMode('upload');
                        setUpdateTargetPackId(null);
                        setIsUploadModalOpen(true);
                    }}
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
                            onUpdate={handleUpdatePack}
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
                            onUpdate={handleUpdatePack}
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
                onClose={handleCloseUploadModal}
                onUploaded={handleUploadCompleted}
                initialFile={pendingUploadFile}
                mode={uploadMode}
                submitUpload={uploadMode === 'update'
                    ? async (file, onProgress) => {
                        if (updateTargetPackId == null) {
                            throw new Error('No modpack selected for update.');
                        }

                        const response = await updateModpack(updateTargetPackId, file, onProgress);
                        if (response.packId == null) {
                            throw new Error('Update did not return a pack ID.');
                        }

                        if (response.packId === updateTargetPackId) {
                            throw new Error('Update returned the same pack ID as the source modpack. A new pack ID is required for migration-based updates.');
                        }

                        return response;
                    }
                    : undefined}
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

            {isGlobalFileDragActive && !isUploadModalOpen && !isMetadataModalOpen && (
                <div className="pointer-events-none fixed inset-0 z-40 flex items-center justify-center bg-slate-950/45 backdrop-blur-sm">
                    <div className="mx-4 w-full max-w-3xl rounded-2xl border-2 border-dashed border-emerald-300/80 bg-emerald-500/10 px-10 py-16 text-center shadow-2xl">
                        <p className="text-2xl font-semibold text-emerald-200">Drop .zip to upload</p>
                        <p className="mt-2 text-sm text-emerald-100/90">Release anywhere to open upload with the file preselected.</p>
                    </div>
                </div>
            )}

            {expandedPack && expandedPack.isDeployed && (
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
                            <span className="-translate-y-px">x</span>
                        </button>
                        <ModpackConsole modpack={expandedPack} />
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
