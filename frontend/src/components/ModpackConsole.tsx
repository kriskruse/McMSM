import { useEffect, useMemo, useState } from 'react';
import type { ModPackCardDto } from '../dto';
import { getPackLogs } from '../util/modpackApi';

type ModpackConsoleProps = {
    modpack: ModPackCardDto;
};

const LOG_POLL_MS = 2_000;

function MetadataTag({ label, value }: { label: string; value: string }) {
    return (
        <div className="rounded-md border border-white/10 bg-slate-950/50 px-3 py-2 text-xs">
            <p className="text-slate-400">{label}</p>
            <p className="mt-1 break-all font-medium text-slate-200">{value || '-'}</p>
        </div>
    );
}

const ModpackConsole = ({ modpack }: ModpackConsoleProps) => {
    const [logs, setLogs] = useState('Loading logs...');
    const [logError, setLogError] = useState('');

    useEffect(() => {
        let isMounted = true;

        const loadLogs = async () => {
            try {
                const nextLogs = await getPackLogs(modpack.packId);
                if (!isMounted) {
                    return;
                }
                setLogs(nextLogs || 'No logs available yet.');
                setLogError('');
            } catch (error) {
                if (!isMounted) {
                    return;
                }
                setLogError(error instanceof Error ? error.message : 'Failed to load logs.');
            }
        };

        void loadLogs();
        const timer = window.setInterval(() => {
            void loadLogs();
        }, LOG_POLL_MS);

        return () => {
            isMounted = false;
            window.clearInterval(timer);
        };
    }, [modpack.packId]);

    const metadataTags = useMemo(
        () => [
            { label: 'Port', value: modpack.port },
            { label: 'Pack version', value: modpack.packVersion },
            { label: 'Minecraft', value: modpack.minecraftVersion },
            { label: 'Java', value: String(modpack.javaVersion) },
            { label: 'Java Xmx', value: modpack.javaXmx },
            { label: 'Entry point', value: modpack.entryPoint },
            { label: 'Container name', value: modpack.containerName ?? '-' },
            { label: 'Container ID', value: modpack.containerId ?? '-' },
        ],
        [modpack],
    );

    return (
        <div>
            <h4 className="mb-3 text-sm font-semibold text-white">{`${modpack.packId} ${modpack.name}`}</h4>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
                <div className="rounded-lg border border-white/10 bg-slate-950 p-3">
                    <p className="mb-2 text-xs text-slate-400">Docker logs</p>
                    <pre className="h-[68vh] overflow-auto whitespace-pre-wrap wrap-break-word font-mono text-xs text-emerald-300">{logs}</pre>
                    {logError && <p className="mt-2 text-xs text-red-400">{logError}</p>}
                </div>
                <div className="flex flex-col gap-2">
                    {metadataTags.map((tag) => (
                        <MetadataTag key={tag.label} label={tag.label} value={tag.value} />
                    ))}
                    {modpack.lastDeployError && (
                        <MetadataTag label="Last deploy error" value={modpack.lastDeployError} />
                    )}
                </div>
            </div>
        </div>
    );
};

export default ModpackConsole;



