import { useCallback, useEffect, useMemo, useOptimistic, useRef, useState, useTransition } from 'react';
import type { ModPackCardDto } from '../dto';
import { archivePack, deletePack, deployPack, getAllPacks, startPack, stopPack } from '../util/modpackApi';
import {BackendStatus} from "../util/healthCheck.ts";

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

export function useModpacks() {
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
            } catch (error) {
                if (error instanceof Error && error.message) {
                    setLoadError(error.message);
                } else {
                    setLoadError('Failed to update modpack state.');
                }
            } finally {
                setPackActionState(packId, false);
            }
        });
    }, [refreshAllPacks, setPackActionState, addOptimistic, startTransition]);

    const handleDeletePack = useCallback((packId: number) => {
        const targetPack = modpacksRef.current.find((pack) => pack.packId === packId);
        const confirmed = window.confirm(`Delete ${targetPack?.name ?? 'this modpack'}? This removes files and metadata.`);
        if (!confirmed) {
            return;
        }
        runPackAction(packId, () => deletePack(packId));
    }, [runPackAction]);

    const handleDeployPack = useCallback((packId: number) => {
        runPackAction(packId, () => deployPack(packId), { status: 'deployed', isDeployed: true });
    }, [runPackAction]);

    const handleStartPack = useCallback((packId: number) => {
        runPackAction(packId, () => startPack(packId), { status: 'running' });
    }, [runPackAction]);

    const handleStopPack = useCallback((packId: number) => {
        runPackAction(packId, () => stopPack(packId), { status: 'stopped' });
    }, [runPackAction]);

    const handleArchivePack = useCallback((packId: number) => {
        const targetPack = modpacksRef.current.find((pack) => pack.packId === packId);
        const confirmed = window.confirm(`Archive ${targetPack?.name ?? 'this modpack'}? This removes only the container.`);
        if (!confirmed) {
            return;
        }
        runPackAction(packId, () => archivePack(packId), { isDeployed: false, status: 'saved' });
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
