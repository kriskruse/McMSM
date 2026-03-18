import { useRef, useState } from 'react';
import type { ChangeEvent, DragEvent } from 'react';
import type { ModPackUploadResponseDto } from '../dto';
import { uploadModpack } from '../util/modpackApi';

type UploadModpackModalProps = {
    isOpen: boolean;
    onClose: () => void;
    onUploaded: (response: ModPackUploadResponseDto) => void;
};

function isZipFile(file: File): boolean {
    return file.name.toLowerCase().endsWith('.zip');
}

const UploadModpackModal = ({ isOpen, onClose, onUploaded }: UploadModpackModalProps) => {
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [error, setError] = useState('');
    const [isDragging, setIsDragging] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [isBackendProcessing, setIsBackendProcessing] = useState(false);
    const [isSuccess, setIsSuccess] = useState(false);

    if (!isOpen) {
        return null;
    }

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

    const handleFile = (file: File | null) => {
        if (!file) {
            return;
        }

        if (!isZipFile(file)) {
            setSelectedFile(null);
            setError('Only .zip files are supported.');
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
            const response = await uploadModpack(selectedFile, (progress) => {
                setUploadProgress(progress);
                if (progress >= 100) {
                    setIsBackendProcessing(true);
                }
            });
            setUploadProgress(100);
            setIsBackendProcessing(false);
            setIsSuccess(true);

            window.setTimeout(() => {
                onUploaded(response);
                clearState();
            }, 900);
        } catch {
            setError('Upload failed. Please try again.');
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
                        <h2 className="text-xl font-semibold text-white">Upload Modpack</h2>
                        <p className="mt-1 text-sm text-slate-400">Drag and drop a .zip file or choose one manually.</p>
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

                <div
                    onDrop={onDrop}
                    onDragOver={onDragOver}
                    onDragLeave={onDragLeave}
                    className={`rounded-xl border-2 border-dashed p-8 text-center transition ${
                        isDragging ? 'border-emerald-400 bg-emerald-500/10' : 'border-white/15 bg-slate-950/40'
                    }`}
                >
                    <p className="text-sm text-slate-300">Drop your modpack .zip file here</p>
                    {selectedFile && (
                        <p className="mt-2 text-xs text-emerald-300">Selected: {selectedFile.name}</p>
                    )}
                </div>

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
                        {isUploading ? 'Uploading...' : 'Start Upload'}
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

                {(isUploading || isSuccess) && (
                    <div className="mt-5">
                        <div className="mb-2 flex items-center justify-between text-xs text-slate-300">
                            <span>
                                {isSuccess
                                    ? 'Upload complete'
                                    : isBackendProcessing
                                        ? 'Upload complete. Backend is extracting and scanning files...'
                                        : 'Uploading modpack...'}
                            </span>
                            <span>{uploadProgress}%</span>
                        </div>
                        <div className="h-2 w-full overflow-hidden rounded-full bg-slate-800">
                            <div
                                className="h-full rounded-full bg-emerald-500 transition-all duration-300"
                                style={{ width: `${uploadProgress}%` }}
                            />
                        </div>
                    </div>
                )}

                {isSuccess && (
                    <div className="mt-5 flex items-center gap-3 rounded-lg border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-emerald-300">
                        <span className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-emerald-300/70 bg-emerald-500/20">
                            <svg
                                className="h-4 w-4 animate-bounce"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="3"
                            >
                                <path d="M5 13l4 4L19 7" />
                            </svg>
                        </span>
                        <span className="text-sm font-medium">Upload finished. Opening metadata review...</span>
                    </div>
                )}

                {error && <p className="mt-4 text-sm text-red-400">{error}</p>}
            </div>
        </div>
    );
};

export default UploadModpackModal;


