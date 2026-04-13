import { useCallback, useMemo, useState } from 'react';
import type { ModPackCardDto, ModPackMetadataResponseDto, ModPackUploadResponseDto } from '../dto';
import { updateModpack } from '../util/modpackApi';

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
        loaderType: pack.loaderType ?? null,
        loaderWarnings: null,
        message: '',
    };
}

type UseUploadFlowOptions = {
    refreshAllPacks: () => Promise<void>;
    setLoadError: (error: string) => void;
};

export function useUploadFlow({ refreshAllPacks, setLoadError }: UseUploadFlowOptions) {
    const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
    const [isMetadataModalOpen, setIsMetadataModalOpen] = useState(false);
    const [pendingUploadResult, setPendingUploadResult] = useState<ModPackUploadResponseDto | null>(null);
    const [pendingUploadFile, setPendingUploadFile] = useState<File | null>(null);
    const [uploadMode, setUploadMode] = useState<'upload' | 'update'>('upload');
    const [updateTargetPackId, setUpdateTargetPackId] = useState<number | null>(null);

    const submitUpload = useMemo(() => {
        if (uploadMode !== 'update' || updateTargetPackId == null) {
            return undefined;
        }

        const targetPackId = updateTargetPackId;
        return async (file: File, onProgress?: (progressPercent: number) => void) => {
            const response = await updateModpack(targetPackId, file, onProgress);

            if (response.packId == null) {
                throw new Error('Update did not return a pack ID.');
            }

            if (response.packId === targetPackId) {
                throw new Error(
                    'Update returned the same pack ID as the source modpack. A new pack ID is required for migration-based updates.',
                );
            }

            return response;
        };
    }, [uploadMode, updateTargetPackId]);

    const openNewUpload = useCallback((file?: File | null) => {
        setPendingUploadFile(file ?? null);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
        setIsUploadModalOpen(true);
    }, []);

    const closeUploadModal = useCallback(() => {
        setIsUploadModalOpen(false);
        setPendingUploadFile(null);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
    }, []);

    const handleUploadCompleted = useCallback((uploadResult: ModPackUploadResponseDto) => {
        setPendingUploadFile(null);
        setUploadMode('upload');
        setUpdateTargetPackId(null);
        setPendingUploadResult(uploadResult);
        setIsUploadModalOpen(false);
        setIsMetadataModalOpen(true);
    }, []);

    const openUpdatePack = useCallback((packId: number) => {
        setLoadError('');
        setPendingUploadFile(null);
        setUploadMode('update');
        setUpdateTargetPackId(packId);
        setIsUploadModalOpen(true);
    }, [setLoadError]);

    const openMetadataForPack = useCallback((pack: ModPackCardDto) => {
        setPendingUploadResult(toUploadResultFromPack(pack));
        setIsMetadataModalOpen(true);
    }, []);

    const closeMetadataModal = useCallback(() => {
        setIsMetadataModalOpen(false);
        setPendingUploadResult(null);
    }, []);

    const handleMetadataSaved = useCallback((response: ModPackMetadataResponseDto) => {
        setIsMetadataModalOpen(false);
        setPendingUploadResult(null);

        if (response.message.toLowerCase().includes('failed')) {
            setLoadError(response.message);
        } else {
            setLoadError('');
        }

        void refreshAllPacks();
    }, [refreshAllPacks, setLoadError]);

    return {
        isUploadModalOpen,
        isMetadataModalOpen,
        pendingUploadResult,
        pendingUploadFile,
        uploadMode,
        submitUpload,
        openNewUpload,
        closeUploadModal,
        handleUploadCompleted,
        openUpdatePack,
        openMetadataForPack,
        closeMetadataModal,
        handleMetadataSaved,
    };
}
