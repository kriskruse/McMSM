import { useCallback, useEffect, useState } from 'react';
import type { UpdateStatusDto } from '../dto';
import { checkForUpdate, forceCheckForUpdate, applyUpdate } from '../util/updateApi';
import { healthCheck } from '../util/healthCheck';

type UpdatePhase = 'idle' | 'confirming' | 'updating' | 'restarting' | 'failed';

export default function UpdateButton() {
    const [status, setStatus] = useState<UpdateStatusDto | null>(null);
    const [phase, setPhase] = useState<UpdatePhase>('idle');
    const [error, setError] = useState('');
    const [checking, setChecking] = useState(false);

    useEffect(() => {
        checkForUpdate().then(setStatus).catch(() => {});
    }, []);

    const handleForceCheck = useCallback(async () => {
        setChecking(true);
        try {
            const result = await forceCheckForUpdate();
            setStatus(result);
        } catch {
            // silently fail — the existing status remains
        } finally {
            setChecking(false);
        }
    }, []);

    const handleApply = useCallback(async () => {
        setPhase('updating');
        setError('');
        try {
            await applyUpdate();
            setPhase('restarting');
            await waitForRestart();
            window.location.reload();
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Update failed');
            setPhase('failed');
        }
    }, []);

    if (!status) {
        return <VersionBadge version="..." />;
    }

    if (status.currentVersion === 'dev') {
        return <VersionBadge version="dev" />;
    }

    if (!status.updateAvailable) {
        return (
            <div className="flex items-center gap-1.5 text-xs text-emerald-400">
                <span className="inline-block h-1.5 w-1.5 rounded-full bg-emerald-400" />
                Latest ({status.currentVersion})
                <RefreshButton onClick={handleForceCheck} spinning={checking} />
            </div>
        );
    }

    return (
        <>
            <div className="flex items-center gap-1.5">
                <button
                    type="button"
                    onClick={() => setPhase('confirming')}
                    disabled={phase !== 'idle' && phase !== 'failed'}
                    className="flex items-center gap-1.5 rounded-md border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-300 transition hover:bg-amber-500/20 disabled:opacity-50"
                >
                    <span className="inline-block h-1.5 w-1.5 rounded-full bg-amber-400" />
                    Update Available ({status.versionsBehind} behind)
                </button>
                <RefreshButton onClick={handleForceCheck} spinning={checking} />
            </div>

            {phase === 'confirming' && (
                <ConfirmDialog
                    currentVersion={status.currentVersion}
                    latestVersion={status.latestVersion}
                    onConfirm={handleApply}
                    onCancel={() => setPhase('idle')}
                />
            )}

            {phase === 'updating' && <OverlayMessage message="Downloading update..." />}
            {phase === 'restarting' && <OverlayMessage message="Restarting server..." />}

            {phase === 'failed' && (
                <div className="fixed bottom-12 right-4 z-50 max-w-xs rounded-lg border border-red-500/30 bg-slate-900 p-3 text-xs text-red-300 shadow-lg">
                    <p className="font-medium">Update failed</p>
                    <p className="mt-1 text-red-400">{error}</p>
                    <button
                        type="button"
                        onClick={() => setPhase('idle')}
                        className="mt-2 text-slate-400 underline hover:text-white"
                    >
                        Dismiss
                    </button>
                </div>
            )}
        </>
    );
}

function VersionBadge({ version }: { version: string }) {
    return <div className="text-xs text-slate-500">{version}</div>;
}

function ConfirmDialog({
    currentVersion,
    latestVersion,
    onConfirm,
    onCancel,
}: {
    currentVersion: string;
    latestVersion: string;
    onConfirm: () => void;
    onCancel: () => void;
}) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4" onClick={onCancel}>
            <div
                className="w-full max-w-sm rounded-2xl border border-white/10 bg-slate-900 p-6 shadow-2xl"
                onClick={(e) => e.stopPropagation()}
            >
                <h3 className="text-lg font-semibold text-white">Update McMSM?</h3>
                <p className="mt-2 text-sm text-slate-300">
                    {currentVersion} &rarr; {latestVersion}
                </p>
                <p className="mt-2 text-xs text-slate-400">
                    The server will download the new version, restart, and clean up the old one.
                    Your modpacks and data will not be affected.
                </p>
                <div className="mt-5 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onCancel}
                        className="rounded-lg border border-white/20 bg-slate-800 px-4 py-2 text-sm text-white transition hover:bg-slate-700"
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={onConfirm}
                        className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-500"
                    >
                        Update Now
                    </button>
                </div>
            </div>
        </div>
    );
}

function OverlayMessage({ message }: { message: string }) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80">
            <div className="flex flex-col items-center gap-3 text-white">
                <div className="h-8 w-8 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                <p className="text-sm font-medium">{message}</p>
            </div>
        </div>
    );
}

function RefreshButton({ onClick, spinning }: { onClick: () => void; spinning: boolean }) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={spinning}
            title="Re-check for updates"
            className="rounded p-0.5 text-slate-500 transition hover:text-slate-300 disabled:opacity-50"
        >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 16 16"
                fill="currentColor"
                className={`h-3 w-3 ${spinning ? 'animate-spin' : ''}`}
            >
                <path
                    fillRule="evenodd"
                    d="M13.836 2.477a.75.75 0 0 1 .75.75v3.182a.75.75 0 0 1-.75.75h-3.182a.75.75 0 0 1 0-1.5h1.37l-.84-.841a4.5 4.5 0 0 0-7.08.681.75.75 0 0 1-1.3-.75 6 6 0 0 1 9.44-.908l.84.84V3.227a.75.75 0 0 1 .75-.75Zm-.911 7.5A.75.75 0 0 1 13.199 11a6 6 0 0 1-9.44.908l-.84-.84v1.456a.75.75 0 0 1-1.5 0V9.342a.75.75 0 0 1 .75-.75h3.182a.75.75 0 0 1 0 1.5h-1.37l.84.841a4.5 4.5 0 0 0 7.08-.681.75.75 0 0 1 1.024-.274Z"
                    clipRule="evenodd"
                />
            </svg>
        </button>
    );
}

async function waitForRestart(): Promise<void> {
    await new Promise((r) => setTimeout(r, 3000));

    for (let i = 0; i < 30; i++) {
        try {
            const result = await healthCheck('');
            if (result === 'online') return;
        } catch {
            // server still down
        }
        await new Promise((r) => setTimeout(r, 2000));
    }
}
