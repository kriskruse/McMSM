import { useLocation } from 'react-router-dom';
import type { ModPackUploadResponseDto } from '../dto';

type UploadCompleteLocationState = {
    uploadResult?: ModPackUploadResponseDto;
};

const UploadComplete = () => {
    const location = useLocation();
    const state = location.state as UploadCompleteLocationState | null;
    const uploadResult = state?.uploadResult;

    return (
        <main className="w-full max-w-4xl px-4 py-6 text-slate-100 md:px-6">
            <section className="rounded-2xl border border-white/10 bg-slate-900/75 p-6">
                <h1 className="text-2xl font-bold text-white">Uploaded Modpack</h1>
                <p className="mt-2 text-sm text-slate-400">
                    Placeholder page for the next setup/deploy step.
                </p>

                {uploadResult && (
                    <div className="mt-4 rounded-lg border border-white/10 bg-slate-950/40 p-4 text-sm text-slate-200">
                        <p>
                            <span className="text-slate-400">Pack:</span> {uploadResult.name ?? 'Unknown'}
                        </p>
                        <p>
                            <span className="text-slate-400">Pack ID:</span> {uploadResult.packId ?? 'Unknown'}
                        </p>
                    </div>
                )}
            </section>
        </main>
    );
};

export default UploadComplete;

