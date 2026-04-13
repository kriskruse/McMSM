import { memo, useEffect, useRef, useState } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import type { ModPackUploadResponseDto } from '../dto';
import { isZipFile } from '../util/fileValidation';
import { uploadModpack } from '../util/modpackApi';
import FileDropZone from './FileDropZone';
import UploadProgress from './UploadProgress';

const SMALL_PACK_SIZE_THRESHOLD = 52_428_800; // 50 MB
const UPLOAD_SUCCESS_DELAY_MS = 900;

function isFileSizeTooLow(size: number): boolean {
    return size < SMALL_PACK_SIZE_THRESHOLD;
}

type UploadModpackModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onUploaded: (response: ModPackUploadResponseDto) => void;
    initialFile?: File | null;
    mode?: 'upload' | 'update';
    submitUpload?: (
        file: File,
        onProgress?: (progressPercent: number) => void,
    ) => Promise<ModPackUploadResponseDto>;
};

const UploadModpackModal = ({
    isOpen,
    onClose,
    onUploaded,
    initialFile = null,
    mode = 'upload',
    submitUpload = uploadModpack,
}: UploadModpackModalProps) => {
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [error, setError] = useState('');
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [isBackendProcessing, setIsBackendProcessing] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);

    const chooseFile = () => {
        fileInputRef.current?.click();
    };

    const clearState = () => {
        setSelectedFile(null);
        setError('');
        setUploadProgress(0);
        setIsBackendProcessing(false);
        setIsUploading(false);
        setIsSuccess(false);
        setIsDragging(false);
    };

    const handleClose = () => {
        if (isUploading) {
            return;
        }

        clearState();
        onClose();
    };

    useEffect(() => {
        if (!initialFile) {
            return;
        }

        if (!isZipFile(initialFile)) {
            setSelectedFile(null);
            setError('Only .zip files are supported.');
            return;
        }

        if (isFileSizeTooLow(initialFile.size)) {
            setSelectedFile(null);
            setError('The size of the file is too small for a modpack. Please make sure that it is a valid modpack');
            return;
        }

        setSelectedFile(initialFile);
        setError('');
    }, [initialFile]);

    if (!isOpen) {
        return null;
    }

    const handleFile = (file: File | null) => {
        if (!file) {
            return;
        }

        if (!isZipFile(file)) {
            setSelectedFile(null);
            setError('Only .zip files are supported.');
            return;
        }
        
        if (isFileSizeTooLow(file.size)) {
            setSelectedFile(null);
            setError('The size of the file is too small for a modpack. Please make sure that it is a valid modpack');
            return;
        }

        setSelectedFile(file);
        setError('');
    };

    const onFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0] ?? null;
        handleFile(file);
    };

    const onDrop = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDragging(false);
        const file = event.dataTransfer.files?.[0] ?? null;
        handleFile(file);
    };

    const onDragOver = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDragging(true);
    };

    const onDragLeave = (event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        setIsDragging(false);
    };

    const startUpload = async () => {
        if (!selectedFile) {
            setError('Choose a .zip file before uploading.');
            return;
        }

        setIsUploading(true);
        setIsBackendProcessing(false);
        setError('');
        setUploadProgress(0);

        try {
            const response = await submitUpload(selectedFile, (progress) => {
                setUploadProgress(progress);
                if (progress >= 100) {
                    setIsBackendProcessing(true);
                }
            });
            setUploadProgress(100);
            setIsSuccess(true);

            window.setTimeout(() => {
                onUploaded(response);
                clearState();
            }, UPLOAD_SUCCESS_DELAY_MS);
        } catch (uploadError) {
            if (uploadError instanceof Error && uploadError.message) {
                setError(uploadError.message);
            } else {
                setError('Upload failed. Please try again.');
            }
            setIsBackendProcessing(false);
            setIsUploading(false);
            setIsSuccess(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4">
            <div className="w-full max-w-2xl rounded-2xl border border-white/10 bg-slate-900 p-6 shadow-2xl">
                <div className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-semibold text-white">
                            {mode === 'update' ? 'Update Modpack' : 'Upload Modpack'}
                        </h2>
                        <p className="mt-1 text-sm text-slate-400">
                            {mode === 'update'
                                ? 'Upload a replacement .zip for this existing modpack.'
                                : 'Drag and drop a .zip file or choose one manually.'}
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={handleClose}
                        className="rounded-md px-2 py-1 text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:opacity-40"
                        disabled={isUploading}
                        aria-label="Close upload modal"
                    >
                        x
                    </button>
                </div>

                <FileDropZone
                    isDragging={isDragging}
                    selectedFileName={selectedFile?.name ?? null}
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    onDragLeave={onDragLeave}
                />

                <input
                    ref={fileInputRef}
                    type="file"
                    accept=".zip,application/zip"
                    className="hidden"
                    onChange={onFileInputChange}
                />

                <div className="mt-4 flex flex-wrap items-center gap-3">
                    <button
                        type="button"
                        onClick={chooseFile}
                        className="rounded-lg border border-white/20 bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
                        disabled={isUploading}
                        aria-label="Choose modpack zip file"
                    >
                        Choose File
                    </button>

                    <button
                        type="button"
                        onClick={() => {
                            void startUpload();
                        }}
                        className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
                        disabled={isUploading || !selectedFile}
                        aria-label="Start uploading selected modpack"
                    >
                        {isUploading ? 'Uploading...' : mode === 'update' ? 'Start Update' : 'Start Upload'}
                    </button>
                </div>

                <p className="mt-3 text-xs text-slate-400">
                    By clicking upload, you agree to Mojang's EULA.
                    {' '}
                    <a
                        href="https://aka.ms/MinecraftEULA"
                        target="_blank"
                        rel="noreferrer"
                        className="text-indigo-300 underline underline-offset-2 hover:text-indigo-200"
                    >
                        Read the EULA
                    </a>
                    .
                </p>

                <UploadProgress
                    isUploading={isUploading}
                    isSuccess={isSuccess}
                    isBackendProcessing={isBackendProcessing}
                    uploadProgress={uploadProgress}
                />

                {error && <p className="mt-4 text-sm text-red-400">{error}</p>}
            </div>
        </div>
    );
};

export default memo(UploadModpackModal);
