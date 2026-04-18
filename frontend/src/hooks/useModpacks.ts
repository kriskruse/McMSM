import { useCallback, useEffect, useMemo, useOptimistic, useRef, useState, useTransition } from 'react';
import type { ModPackCardDto } from '../dto';
import { archivePack, deletePack, deployPack, getAllPacks, startPack, stopPack } from '../util/modpackApi';
import {BackendStatus} from "../util/healthCheck.ts";
import type { ToastVariant } from './useToast';

type OptimisticUpdate = { packId: number; changes: Partial<ModPackCardDto> };



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

type UseModpacksOptions = {
    addToast?: (message: string, variant?: ToastVariant) => void;
};

export function useModpacks({ addToast }: UseModpacksOptions = {}) {
    const [modpacks, setModpacks] = useState<ModPackCardDto[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking');
    const [activePackActions, setActivePackActions] = useState<number[]>([]);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [, startTransition] = useTransition();
    const [optimisticModpacks, addOptimistic] = useOptimistic(
        modpacks,
        (currentModpacks, { packId, changes }: OptimisticUpdate) =>
            currentModpacks.map(pack =>
                pack.packId === packId ? { ...pack, ...changes } : pack
            )
    );
    const modpacksRef = useRef(modpacks);
    modpacksRef.current = modpacks;

    const refreshAllPacks = useCallback(async () => {
        try {
            const packs = await getAllPacks();
            setModpacks(packs);
            setBackendStatus('online');
        } catch (error) {
            if (isConnectionError(error)) {
                setBackendStatus('offline');
            } else {
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

    const deployedModpacks = useMemo(() => optimisticModpacks.filter((pack) => pack.isDeployed), [optimisticModpacks]);
    const nonDeployedModpacks = useMemo(() => optimisticModpacks.filter((pack) => !pack.isDeployed), [optimisticModpacks]);

    const setPackActionState = useCallback((packId: number, isActive: boolean) => {
        setActivePackActions((previous) => {
            if (isActive) {
                if (previous.includes(packId)) {
                    return previous;
                }
                return [...previous, packId];
            }
            return previous.filter((id) => id !== packId);
        });
    }, []);

    const runPackAction = useCallback((
        packId: number,
        action: () => Promise<void>,
        optimisticChanges?: Partial<ModPackCardDto>,
        successMessage?: string,
    ) => {
        setPackActionState(packId, true);
        setLoadError('');

        startTransition(async () => {
            if (optimisticChanges) {
                addOptimistic({ packId, changes: optimisticChanges });
            }
            try {
                await action();
                await refreshAllPacks();
                if (successMessage) {
                    addToast?.(successMessage, 'success');
                }
            } catch (error) {
                const message = error instanceof Error && error.message
                    ? error.message
                    : 'Failed to update modpack state.';
                setLoadError(message);
                addToast?.(message, 'error');
            } finally {
                setPackActionState(packId, false);
            }
        });
    }, [refreshAllPacks, setPackActionState, addOptimistic, startTransition, addToast]);

    const handleDeletePack = useCallback((packId: number) => {
        const targetPack = modpacksRef.current.find((pack) => pack.packId === packId);
        const packName = targetPack?.name ?? 'Modpack';
        const confirmed = window.confirm(`Delete ${packName}? This removes files and metadata.`);
        if (!confirmed) {
            return;
        }
        runPackAction(packId, () => deletePack(packId), undefined, `${packName} deleted`);
    }, [runPackAction]);

    const handleDeployPack = useCallback((packId: number) => {
        const packName = modpacksRef.current.find((p) => p.packId === packId)?.name ?? 'Modpack';
        runPackAction(packId, () => deployPack(packId), { status: 'deployed', isDeployed: true }, `${packName} deployed`);
    }, [runPackAction]);

    const handleStartPack = useCallback((packId: number) => {
        const packName = modpacksRef.current.find((p) => p.packId === packId)?.name ?? 'Modpack';
        runPackAction(packId, () => startPack(packId), { status: 'running' }, `${packName} started`);
    }, [runPackAction]);

    const handleStopPack = useCallback((packId: number) => {
        const packName = modpacksRef.current.find((p) => p.packId === packId)?.name ?? 'Modpack';
        runPackAction(packId, () => stopPack(packId), { status: 'stopped' }, `${packName} stopped`);
    }, [runPackAction]);

    const handleArchivePack = useCallback((packId: number) => {
        const targetPack = modpacksRef.current.find((pack) => pack.packId === packId);
        const packName = targetPack?.name ?? 'Modpack';
        const confirmed = window.confirm(`Archive ${packName}? This removes only the container.`);
        if (!confirmed) {
            return;
        }
        runPackAction(packId, () => archivePack(packId), { isDeployed: false, status: 'saved' }, `${packName} archived`);
    }, [runPackAction]);

    return {
        modpacks: optimisticModpacks,
        deployedModpacks,
        nonDeployedModpacks,
        isLoading,
        loadError,
        setLoadError,
        backendStatus,
        activePackActions,
        isRefreshing,
        refreshAllPacks,
        handleManualRefresh,
        handleDeletePack,
        handleDeployPack,
        handleStartPack,
        handleStopPack,
        handleArchivePack,
    };
}
