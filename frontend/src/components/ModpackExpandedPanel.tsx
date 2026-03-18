import { useEffect, useMemo, useState } from 'react';
import type { ModPackCardDto, ModPackMetadataRequestDto } from '../dto';
import { getPackLogs, updatePackMetadata } from '../util/modpackApi';

type ModpackExpandedPanelProps = {
    modpack: ModPackCardDto;
    onMetadataSaved: () => void;
};

type MetadataForm = {
    name: string;
    packVersion: string;
    minecraftVersion: string;
    javaVersion: number;
    javaXmx: string;
    port: string;
    entryPoint: string;
};

const LOG_POLL_MS = 2_000;

const inputClass =
    'mt-1 block w-full rounded-md bg-white/5 px-3 py-2 text-white outline outline-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:outline-indigo-500 sm:text-sm';

function toMetadataForm(modpack: ModPackCardDto): MetadataForm {
    return {
        name: modpack.name,
        packVersion: modpack.packVersion,
        minecraftVersion: modpack.minecraftVersion,
        javaVersion: modpack.javaVersion,
        javaXmx: modpack.javaXmx,
        port: modpack.port,
        entryPoint: modpack.entryPoint,
    };
}

function MetadataTag({ label, value }: { label: string; value: string }) {
    return (
        <div className="rounded-md border border-white/10 bg-slate-950/50 px-3 py-2 text-xs">
            <p className="text-slate-400">{label}</p>
            <p className="mt-1 break-all font-medium text-slate-200">{value || '-'}</p>
        </div>
    );
}

const ModpackExpandedPanel = ({ modpack, onMetadataSaved }: ModpackExpandedPanelProps) => {
    const [logs, setLogs] = useState('Loading logs...');
    const [logError, setLogError] = useState('');
    const [form, setForm] = useState<MetadataForm>(() => toMetadataForm(modpack));
    const [saveError, setSaveError] = useState('');
    const [saveSuccess, setSaveSuccess] = useState('');
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        setForm(toMetadataForm(modpack));
        setSaveError('');
        setSaveSuccess('');
    }, [modpack]);

    useEffect(() => {
        if (!modpack.isDeployed) {
            setLogs('Logs are available after deployment.');
            setLogError('');
            return;
        }

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
    }, [modpack.isDeployed, modpack.packId]);

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

    const updateForm = <T extends keyof MetadataForm>(key: T, value: MetadataForm[T]) => {
        setForm((previous) => ({
            ...previous,
            [key]: value,
        }));
    };

    const handleSave = async () => {
        if (modpack.isDeployed) {
            return;
        }

        setIsSaving(true);
        setSaveError('');
        setSaveSuccess('');

        const payload: ModPackMetadataRequestDto = {
            name: form.name,
            packVersion: form.packVersion,
            minecraftVersion: form.minecraftVersion,
            javaVersion: Number(form.javaVersion),
            javaXmx: form.javaXmx,
            port: form.port,
            entryPoint: form.entryPoint,
        };

        try {
            await updatePackMetadata(modpack.packId, payload);
            setSaveSuccess('Metadata saved.');
            onMetadataSaved();
        } catch (error) {
            setSaveError(error instanceof Error ? error.message : 'Failed to save metadata.');
        } finally {
            setIsSaving(false);
        }
    };

    if (modpack.isDeployed) {
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
    }

    return (
        <div>
            <h4 className="mb-3 text-sm font-semibold text-white">{`${modpack.packId} ${modpack.name}`}</h4>
            <p className="mb-4 text-xs text-slate-400">Edit metadata before deploying this modpack.</p>

            <div className="grid gap-3 md:grid-cols-2">
                <label>
                    <span className="text-xs text-slate-300">Name</span>
                    <input className={inputClass} value={form.name} onChange={(event) => updateForm('name', event.target.value)} />
                </label>
                <label>
                    <span className="text-xs text-slate-300">Pack Version</span>
                    <input className={inputClass} value={form.packVersion} onChange={(event) => updateForm('packVersion', event.target.value)} />
                </label>
                <label>
                    <span className="text-xs text-slate-300">Minecraft Version</span>
                    <input className={inputClass} value={form.minecraftVersion} onChange={(event) => updateForm('minecraftVersion', event.target.value)} />
                </label>
                <label>
                    <span className="text-xs text-slate-300">Java Version</span>
                    <input
                        className={`${inputClass} cursor-not-allowed text-slate-400`}
                        type="number"
                        min={8}
                        max={25}
                        value={form.javaVersion}
                        readOnly
                    />
                </label>
                <label>
                    <span className="text-xs text-slate-300">Java Xmx</span>
                    <input className={inputClass} value={form.javaXmx} onChange={(event) => updateForm('javaXmx', event.target.value)} />
                </label>
                <label>
                    <span className="text-xs text-slate-300">Port</span>
                    <input className={inputClass} value={form.port} onChange={(event) => updateForm('port', event.target.value)} />
                </label>
                <label className="md:col-span-2">
                    <span className="text-xs text-slate-300">Entry point</span>
                    <input className={inputClass} value={form.entryPoint} onChange={(event) => updateForm('entryPoint', event.target.value)} />
                </label>
            </div>

            {saveError && <p className="mt-3 text-xs text-red-400">{saveError}</p>}
            {saveSuccess && <p className="mt-3 text-xs text-emerald-300">{saveSuccess}</p>}

            <div className="mt-4 flex justify-end">
                <button
                    type="button"
                    onClick={() => {
                        void handleSave();
                    }}
                    disabled={isSaving}
                    className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-indigo-500 disabled:opacity-50"
                    aria-label="Save expanded metadata changes"
                >
                    {isSaving ? 'Saving...' : 'Save Metadata'}
                </button>
            </div>
        </div>
    );
};

export default ModpackExpandedPanel;


