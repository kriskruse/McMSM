import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import ModpackCard from '../components/ModpackCard.tsx';
import ModpackConsole from '../components/ModpackConsole.tsx';
import ModpackMetadataModal from '../components/ModpackMetadataModal.tsx';
import UploadModpackModal from '../components/UploadModpackModal.tsx';
import { useDragDrop } from '../hooks/useDragDrop';
import { useModpacks } from '../hooks/useModpacks';
import { useUploadFlow } from '../hooks/useUploadFlow';
import { isZipFile } from '../util/fileValidation';
import StatusBox from "../components/StatusBox.tsx";
import UpdateButton from "../components/UpdateButton.tsx";
import {healthCheck} from "../util/healthCheck.ts";
import {BackendStatus} from "../util/healthCheck.ts";
import CloseButton from "../components/CloseButton";

const Home = () => {
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
    } = useModpacks();

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
            <header className="mb-6 flex flex-col gap-4 rounded-2xl border border-white/10 bg-slate-900/75 p-5 md:flex-row md:items-center md:justify-between">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-white md:text-3xl">
                        MC Modded Server Manager
                    </h1>
                    <p className="mt-1 text-sm text-slate-400">
                        <span className="text-xs">McMSM</span>
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <StatusBox text="Backend" status={backendStatus} />
                    <StatusBox text="Docker" status={dockerStatus} />
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
                    onClick={() => openNewUpload()}
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
