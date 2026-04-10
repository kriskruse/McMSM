import { memo } from 'react';

type UploadProgressProps = {
    isUploading: boolean;
    isSuccess: boolean;
    isBackendProcessing: boolean;
    uploadProgress: number;
};

const UploadProgress = ({ isUploading, isSuccess, isBackendProcessing, uploadProgress }: UploadProgressProps) => {
    if (!isUploading && !isSuccess) {
        return null;
    }

    return (
        <>
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
