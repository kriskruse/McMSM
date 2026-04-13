import { memo, useEffect, useState } from 'react';

const PROCESSING_MESSAGES = [
    'Extracting modpack archive...',
    'Scanning for mod loader...',
    'Reading server configuration...',
    'Detecting Minecraft version...',
    'Preparing server files...',
    'Almost there...',
];

const CYCLE_INTERVAL_MS = 3_000;

type UploadProgressProps = {
    isUploading: boolean;
    isSuccess: boolean;
    isBackendProcessing: boolean;
    uploadProgress: number;
};

const UploadProgress = ({ isUploading, isSuccess, isBackendProcessing, uploadProgress }: UploadProgressProps) => {
    const [messageIndex, setMessageIndex] = useState(0);

    useEffect(() => {
        if (!isBackendProcessing) {
            setMessageIndex(0);
            return;
        }

        const interval = setInterval(() => {
            setMessageIndex((prev) => (prev + 1) % PROCESSING_MESSAGES.length);
        }, CYCLE_INTERVAL_MS);

        return () => clearInterval(interval);
    }, [isBackendProcessing]);

    if (!isUploading && !isSuccess) {
        return null;
    }

    return (
        <>
            {isBackendProcessing ? (
                <div className="mt-5 flex flex-col items-center gap-3 py-2">
                    <svg
                        className="h-10 w-10 animate-spin text-emerald-400"
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                    >
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
                    </svg>
                    <span className="text-sm text-slate-300 transition-opacity duration-300">
                        {PROCESSING_MESSAGES[messageIndex]}
                    </span>
                </div>
            ) : (
                <div className="mt-5">
                    <div className="mb-2 flex items-center justify-between text-xs text-slate-300">
                        <span>{isSuccess ? 'Upload complete' : 'Uploading modpack...'}</span>
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
        </>
    );
};

export default memo(UploadProgress);
