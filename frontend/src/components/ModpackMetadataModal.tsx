import { useEffect, useMemo, useState } from 'react';
import compatData from '../data/minecraft_java_compat.json';
import type {
    ModPackMetadataRequestDto,
    ModPackMetadataResponseDto,
    ModPackUploadResponseDto,
} from '../dto';
import { updatePackMetadata } from '../util/modpackApi';

type CompatRange = {
    min: string;
    max: string;
    java: number;
};

type CompatConfig = {
    ranges: CompatRange[];
    default_java: number;
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

type ModpackMetadataModalProps = {
    isOpen: boolean;
    uploadResult: ModPackUploadResponseDto | null;
    onClose: () => void;
    onSaved: (response: ModPackMetadataResponseDto) => void;
};

const compat = compatData as CompatConfig;

const minecraftVersionGroups = compat.ranges
    .map((range) => ({
        ...range,
        value: range.max,
        label: `${range.min} - ${range.max} (Java ${range.java})`,
    }))
    .reverse();

function compareVersions(left: string, right: string): number {
    const leftParts = left.split('.').map((part) => Number.parseInt(part, 10));
    const rightParts = right.split('.').map((part) => Number.parseInt(part, 10));
    const maxLength = Math.max(leftParts.length, rightParts.length);

    for (let index = 0; index < maxLength; index += 1) {
        const leftValue = leftParts[index] ?? 0;
        const rightValue = rightParts[index] ?? 0;
        if (leftValue > rightValue) {
            return 1;
        }
        if (leftValue < rightValue) {
            return -1;
        }
    }

    return 0;
}

function mapJavaFromMinecraftVersion(minecraftVersion: string): number {
    for (const range of compat.ranges) {
        if (compareVersions(minecraftVersion, range.min) >= 0 && compareVersions(minecraftVersion, range.max) <= 0) {
            return range.java;
        }
    }
    return compat.default_java;
}

function resolveGroupVersion(minecraftVersion: string | null): string {
    if (minecraftVersion) {
        for (const range of compat.ranges) {
            if (compareVersions(minecraftVersion, range.min) >= 0 && compareVersions(minecraftVersion, range.max) <= 0) {
                return range.max;
            }
        }
    }

    return minecraftVersionGroups[0]?.value ?? '1.21.11';
}

function resolveEntryPointCandidates(uploadResult: ModPackUploadResponseDto): string[] {
    if (uploadResult.entryPointCandidates && uploadResult.entryPointCandidates.length > 0) {
        return uploadResult.entryPointCandidates;
    }
    return ['startserver.sh'];
}

function buildInitialForm(uploadResult: ModPackUploadResponseDto): MetadataForm {
    const minecraftVersion = resolveGroupVersion(
        uploadResult.minecraftVersion && uploadResult.minecraftVersion !== 'unknown'
            ? uploadResult.minecraftVersion
            : null,
    );

    const entryPointCandidates = resolveEntryPointCandidates(uploadResult);
    const detectedEntryPoint = uploadResult.entryPoint ?? entryPointCandidates[0] ?? 'startserver.sh';

    return {
        name: uploadResult.name ?? '',
        packVersion: uploadResult.packVersion ?? 'unknown',
        minecraftVersion,
        javaVersion: mapJavaFromMinecraftVersion(minecraftVersion),
        javaXmx: uploadResult.javaXmx ?? '5G',
        port: uploadResult.port ?? '25565',
        entryPoint: entryPointCandidates.includes(detectedEntryPoint)
            ? detectedEntryPoint
            : entryPointCandidates[0],
    };
}

const inputClass =
    'mt-1 block w-full rounded-md bg-white/5 px-3 py-2 text-white outline outline-1 outline-white/10 placeholder:text-gray-500 focus:outline-2 focus:outline-indigo-500 sm:text-sm';

const ModpackMetadataModal = ({ isOpen, uploadResult, onClose, onSaved }: ModpackMetadataModalProps) => {
    const [form, setForm] = useState<MetadataForm | null>(null);
    const [error, setError] = useState('');
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (isOpen && uploadResult) {
            setForm(buildInitialForm(uploadResult));
            setError('');
        }
    }, [isOpen, uploadResult]);

    const canRender = isOpen && uploadResult && form;
    const packId = uploadResult?.packId ?? null;

    const title = useMemo(() => {
        if (!uploadResult?.name) {
            return 'Review Metadata';
        }
        return `Review Metadata: ${uploadResult.name}`;
    }, [uploadResult?.name]);

    const entryPointCandidates = useMemo(() => {
        if (!uploadResult) {
            return ['startserver.sh'];
        }
        return resolveEntryPointCandidates(uploadResult);
    }, [uploadResult]);

    if (!canRender) {
        return null;
    }

    const updateForm = <T extends keyof MetadataForm>(key: T, value: MetadataForm[T]) => {
        setForm((previous) => {
            if (!previous) {
                return previous;
            }
            return {
                ...previous,
                [key]: value,
            };
        });
    };

    const onMinecraftVersionChange = (minecraftVersion: string) => {
        updateForm('minecraftVersion', minecraftVersion);
        updateForm('javaVersion', mapJavaFromMinecraftVersion(minecraftVersion));
    };

    const submit = async () => {
        if (!form || packId == null) {
            return;
        }

        setIsSaving(true);
        setError('');

        const request: ModPackMetadataRequestDto = {
            name: form.name,
            packVersion: form.packVersion,
            minecraftVersion: form.minecraftVersion,
            javaVersion: form.javaVersion,
            javaXmx: form.javaXmx,
            port: form.port,
            entryPoint: form.entryPoint,
        };

        try {
            const response = await updatePackMetadata(packId, request);
            onSaved(response);
        } catch (submitError) {
            if (submitError instanceof Error && submitError.message) {
                setError(submitError.message);
            } else {
                setError('Failed to save metadata.');
            }
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4">
            <div className="w-full max-w-3xl rounded-2xl border border-white/10 bg-slate-900 p-6 shadow-2xl">
                <div className="mb-5 flex items-start justify-between gap-4">
                    <div>
                        <h2 className="text-xl font-semibold text-white">{title}</h2>
                        <p className="mt-1 text-sm text-slate-400">Confirm detected values before deployment.</p>
                    </div>
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={isSaving}
                        className="rounded-md px-2 py-1 text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:opacity-40"
                        aria-label="Close metadata modal"
                    >
                        x
                    </button>
                </div>

                <div className="grid gap-4 md:grid-cols-2">
                    <label>
                        <span className="text-sm text-slate-300">Name</span>
                        <input className={inputClass} value={form.name} onChange={(event) => updateForm('name', event.target.value)} />
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Pack Version</span>
                        <input className={inputClass} value={form.packVersion} onChange={(event) => updateForm('packVersion', event.target.value)} />
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Minecraft Version</span>
                        <select
                            className={inputClass}
                            value={form.minecraftVersion}
                            onChange={(event) => onMinecraftVersionChange(event.target.value)}
                        >
                            {minecraftVersionGroups.map((group) => (
                                <option key={group.value} value={group.value} className="bg-slate-900 text-white">
                                    {group.label}
                                </option>
                            ))}
                        </select>
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Java Version</span>
                        <input className={`${inputClass} cursor-not-allowed text-slate-400`} value={form.javaVersion} readOnly />
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Java Xmx</span>
                        <input className={inputClass} value={form.javaXmx} onChange={(event) => updateForm('javaXmx', event.target.value)} />
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Port</span>
                        <input className={inputClass} value={form.port} onChange={(event) => updateForm('port', event.target.value)} />
                    </label>

                    <label>
                        <span className="text-sm text-slate-300">Entry Point</span>
                        <select
                            className={inputClass}
                            value={form.entryPoint}
                            onChange={(event) => updateForm('entryPoint', event.target.value)}
                        >
                            {entryPointCandidates.map((entryPointCandidate) => (
                                <option key={entryPointCandidate} value={entryPointCandidate} className="bg-slate-900 text-white">
                                    {entryPointCandidate}
                                </option>
                            ))}
                        </select>
                    </label>
                </div>

                {error && <p className="mt-4 text-sm text-red-400">{error}</p>}

                <div className="mt-6 flex justify-end gap-3">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={isSaving}
                        className="rounded-lg border border-white/20 bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                        aria-label="Cancel metadata changes"
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        onClick={() => {
                            void submit();
                        }}
                        disabled={isSaving}
                        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-indigo-500 disabled:opacity-50"
                        aria-label="Save metadata"
                    >
                        {isSaving ? 'Saving...' : 'Save Metadata'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ModpackMetadataModal;

