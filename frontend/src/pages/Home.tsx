import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import ModpackCard from '../components/ModpackCard.tsx';
import ModpackConsole from '../components/ModpackConsole.tsx';
import ModpackMetadataModal from '../components/ModpackMetadataModal.tsx';
import UploadModpackModal from '../components/UploadModpackModal.tsx';
import { useDragDrop } from '../hooks/useDragDrop';
import { useModpacks } from '../hooks/useModpacks';
import { useToast } from '../hooks/useToast';
import { useUploadFlow } from '../hooks/useUploadFlow';
import { btn } from '../util/buttonVariants';
import { isZipFile } from '../util/fileValidation';
import SkeletonCard from '../components/SkeletonCard';
import SystemStatusPanel from '../components/SystemStatusPanel';
import UpdateButton from "../components/UpdateButton.tsx";
import {healthCheck} from "../util/healthCheck.ts";
import {BackendStatus} from "../util/healthCheck.ts";
import CloseButton from "../components/CloseButton";

const Home = () => {
    const { addToast } = useToast();
    const {
        modpacks,
        deployedModpacks,
        nonDeployedModpacks,
        isLoading,
        loadError,
        setLoadError,
        activePackActions,
        isRefreshing,
        refreshAllPacks,
        handleManualRefresh,
        handleDeletePack,
        handleDeployPack,
        handleStartPack,
        handleStopPack,
        handleArchivePack,
    } = useModpacks({ addToast });

    const {
        isUploadModalOpen,
        isMetadataModalOpen,
        pendingUploadResult,
        pendingUploadFile,
        uploadMode,
        updateTargetPackName,
        updateTargetPackId,
        openNewUpload,
        closeUploadModal,
        handleUploadCompleted,
        openUpdatePack,
        openMetadataForPack,
        closeMetadataModal,
        handleMetadataSaved,
    } = useUploadFlow({ refreshAllPacks, setLoadError });

    useEffect(() => {
        const pollHealth = () => {
            healthCheck('/docker').then(setDockerStatus).catch(() => setDockerStatus('offline'));
            healthCheck('').then(setBackendStatus).catch(() => setBackendStatus('offline'));
        };
        pollHealth();
        const intervalId = window.setInterval(pollHealth, 2000);
        return () => window.clearInterval(intervalId);
    }, []);
    const [dockerStatus, setDockerStatus] = useState<BackendStatus>('checking');
    const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking');

    const [expandedPackId, setExpandedPackId] = useState<number | null>(null);
    const modpacksRef = useRef(modpacks);
    modpacksRef.current = modpacks;

    const handleFileDrop = useCallback((file: File) => {
        if (isUploadModalOpen) {
            return;
        }
        if (!isZipFile(file)) {
            setLoadError('Only .zip files are supported for upload.');
            return;
        }
        setLoadError('');
        openNewUpload(file);
    }, [setLoadError, openNewUpload, isUploadModalOpen]);

    const { isActive: isGlobalFileDragActive } = useDragDrop(handleFileDrop);

    const toggleExpandedPack = useCallback((packId: number) => {
        const targetPack = modpacksRef.current.find((pack) => pack.packId === packId);
        if (!targetPack) {
            return;
        }

        if (!targetPack.isDeployed) {
            setExpandedPackId(null);
            openMetadataForPack(targetPack);
            return;
        }

        setExpandedPackId((previous) => (previous === packId ? null : packId));
    }, [openMetadataForPack]);

    const expandedPack = useMemo(
        () => modpacks.find((pack) => pack.packId === expandedPackId) ?? null,
        [modpacks, expandedPackId],
    );

    const closeConsole = useCallback(() => setExpandedPackId(null), []);

    return (
        <main className="w-full max-w-6xl px-4 py-6 text-slate-100 md:px-6">
            <header className="mb-6 flex flex-col gap-4 rounded-2xl border border-white/10 bg-slate-900/75 p-5 md:flex-row md:items-center md:justify-between md:gap-6">
                <div className="min-w-0">
                    <h1 className="text-2xl font-bold tracking-tight text-white md:text-3xl">
                        MC Modded Server Manager
                    </h1>
                    <p className="mt-1 text-sm text-slate-400">
                        <span className="text-xs">McMSM</span>
                    </p>
                </div>
                <div className="w-full md:w-[45%] md:max-w-[45%] md:shrink-0">
                    <SystemStatusPanel backendStatus={backendStatus} dockerStatus={dockerStatus} />
                </div>
            </header>

            <div className="mb-4 flex justify-end gap-2">
                <button
                    type="button"
                    onClick={() => {
                        void handleManualRefresh();
                    }}
                    className={`${btn('ghost')} gap-2`}
                    aria-label="Refresh modpacks"
                    disabled={isRefreshing || isLoading}
                >
                    {isRefreshing ? 'Refreshing...' : 'Refresh'}
                </button>
                <button
                    type="button"
                    onClick={() => openNewUpload()}
                    className={`${btn('success')} gap-2`}
                    aria-label="Upload new modpack"
                >
                    <span className="text-lg leading-none">+</span>
                    Upload Modpack
                </button>
            </div>

            <section className="mb-6 rounded-2xl border border-white/10 bg-slate-900/70 p-5">
                <h2 className="mb-4 text-lg font-semibold text-white">Currently Deployed Modpacks</h2>
                {isLoading && (
                    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        <SkeletonCard />
                        <SkeletonCard />
                        <SkeletonCard />
                    </div>
                )}
                {!isLoading && loadError && <p className="text-sm text-red-400">{loadError}</p>}
                {!isLoading && !loadError && deployedModpacks.length === 0 && (
                    <div className="flex flex-col items-center gap-3 py-10 text-center">
                        <svg className="h-12 w-12 text-slate-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
                            <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
                            <line x1="12" y1="22.08" x2="12" y2="12" />
                        </svg>
                        <p className="text-sm text-slate-400">No deployed servers yet.</p>
                        <p className="text-xs text-slate-500">Upload a modpack and deploy it to get started.</p>
                    </div>
                )}
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {deployedModpacks.map((pack) => (
                        <ModpackCard
                            key={pack.packId}
                            modpack={pack}
                            isBusy={activePackActions.includes(pack.packId)}
                            isExpanded={expandedPackId === pack.packId}
                            onToggleExpand={toggleExpandedPack}
                            onUpdate={openUpdatePack}
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
                {!isLoading && nonDeployedModpacks.length === 0 && (
                    <div className="flex flex-col items-center gap-3 py-10 text-center">
                        <svg className="h-12 w-12 text-slate-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                            <line x1="12" y1="18" x2="12" y2="12" />
                            <line x1="9" y1="15" x2="15" y2="15" />
                        </svg>
                        <p className="text-sm text-slate-400">No saved instances.</p>
                        <button
                            type="button"
                            onClick={() => openNewUpload()}
                            className="mt-1 text-sm font-medium text-indigo-400 hover:text-indigo-300"
                        >
                            Upload your first modpack
                        </button>
                    </div>
                )}
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                    {nonDeployedModpacks.map((pack) => (
                        <ModpackCard
                            key={pack.packId}
                            modpack={pack}
                            isBusy={activePackActions.includes(pack.packId)}
                            isExpanded={expandedPackId === pack.packId}
                            onToggleExpand={toggleExpandedPack}
                            onUpdate={openUpdatePack}
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
                onClose={closeUploadModal}
                onUploaded={handleUploadCompleted}
                initialFile={pendingUploadFile}
                mode={uploadMode}
                updatePackName={updateTargetPackName}
                updateTargetPackId={updateTargetPackId}
            />

            <ModpackMetadataModal
                isOpen={isMetadataModalOpen}
                uploadResult={pendingUploadResult}
                existingPorts={modpacks.map((pack) => ({ packId: pack.packId, name: pack.name, port: pack.port }))}
                onClose={closeMetadataModal}
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
                    role="dialog"
                    aria-modal="true"
                    aria-label={`Console for ${expandedPack.name}`}
                    onClick={closeConsole}
                >
                    <div
                        className="relative w-full max-w-7xl max-h-[92vh] overflow-auto rounded-2xl border border-white/10 bg-slate-900 p-8 shadow-2xl"
                        onClick={(event) => event.stopPropagation()}
                    >
                        <CloseButton onClick={closeConsole} className="absolute right-4 top-4" />
                        <ModpackConsole modpack={expandedPack} />
                    </div>
                </div>
            )}

            <div className="fixed bottom-3 right-4">
                <UpdateButton />
            </div>
        </main>
    );
};

export default Home;
