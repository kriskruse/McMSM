import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import CircularSpinner from '../components/CircularSpinner';
import ConfigFileList from '../components/config/ConfigFileList';
import JsonTreeEditor from '../components/config/JsonTreeEditor';
import type { ConfigFileDto, ModPackCardDto } from '../dto';
import { useToast } from '../hooks/useToast';
import { btn } from '../util/buttonVariants';
import {
    getAllPacks,
    listConfigFiles,
    readConfigFile,
    startPack,
    stopPack,
    writeConfigFile,
} from '../util/modpackApi';
import {
    isContainer,
    parseJsonc,
    setLeafAtPath,
    stringifyJsonc,
    type ContainerNode,
    type JsonModel,
} from '../util/jsonc';

const UNSAVED_PROMPT = 'You have unsaved changes. Discard them?';

const ConfigEditor = () => {
    const { packId: packIdParam } = useParams<{ packId: string }>();
    const packId = Number(packIdParam);
    const isValidPackId = Number.isFinite(packId);
    const navigate = useNavigate();
    const { addToast } = useToast();

    const [pack, setPack] = useState<ModPackCardDto | null>(null);
    const [files, setFiles] = useState<ConfigFileDto[]>([]);
    const [loadError, setLoadError] = useState('');
    const [isLoadingList, setIsLoadingList] = useState(true);

    const [selectedPath, setSelectedPath] = useState<string | null>(null);
    const [isLoadingFile, setIsLoadingFile] = useState(false);
    const [parseError, setParseError] = useState('');
    const [rawText, setRawText] = useState('');
    const [dirty, setDirty] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [model, setModel] = useState<JsonModel>(null);

    useEffect(() => {
        if (!isValidPackId) {
            return undefined;
        }
        let cancelled = false;
        Promise.all([getAllPacks(), listConfigFiles(packId)])
            .then(([packs, configFiles]) => {
                if (cancelled) return;
                setPack(packs.find((candidate) => candidate.packId === packId) ?? null);
                setFiles(configFiles);
                setLoadError('');
            })
            .catch((error: unknown) => {
                if (cancelled) return;
                setLoadError(error instanceof Error ? error.message : 'Failed to load configs.');
            })
            .finally(() => {
                if (!cancelled) setIsLoadingList(false);
            });
        return () => {
            cancelled = true;
        };
    }, [isValidPackId, packId]);

    useEffect(() => {
        if (!dirty) return undefined;
        const handler = (event: BeforeUnloadEvent) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', handler);
        return () => window.removeEventListener('beforeunload', handler);
    }, [dirty]);

    const openFile = useCallback(
        (relativePath: string) => {
            if (relativePath === selectedPath) return;
            if (dirty && !window.confirm(UNSAVED_PROMPT)) return;

            setSelectedPath(relativePath);
            setDirty(false);
            setParseError('');
            setIsLoadingFile(true);
            setModel(null);

            readConfigFile(packId, relativePath)
                .then((content) => {
                    setRawText(content);
                    try {
                        setModel(parseJsonc(content));
                        setParseError('');
                    } catch (error) {
                        setModel(null);
                        setParseError(
                            error instanceof Error ? error.message : 'Could not parse JSON.',
                        );
                    }
                })
                .catch((error: unknown) => {
                    setParseError(
                        error instanceof Error ? error.message : 'Failed to read file.',
                    );
                })
                .finally(() => setIsLoadingFile(false));
        },
        [dirty, packId, selectedPath],
    );

    const handleLeafChange = useCallback(
        (path: string[], text: string) => {
            if (model === null) return;
            setLeafAtPath(model, path, text);
            setDirty(true);
        },
        [model],
    );

    const handleRawChange = useCallback((text: string) => {
        setRawText(text);
        setDirty(true);
    }, []);

    const serializeCurrent = useCallback((): string => {
        if (parseError || model === null) {
            return rawText;
        }
        return stringifyJsonc(model);
    }, [model, parseError, rawText]);

    const persist = useCallback(async (): Promise<boolean> => {
        if (selectedPath === null) return false;
        setIsSaving(true);
        try {
            await writeConfigFile(packId, selectedPath, serializeCurrent());
            setDirty(false);
            return true;
        } catch (error) {
            addToast(error instanceof Error ? error.message : 'Failed to save.', 'error');
            return false;
        } finally {
            setIsSaving(false);
        }
    }, [addToast, packId, selectedPath, serializeCurrent]);

    const handleSave = useCallback(async () => {
        if (await persist()) {
            addToast('Config saved.', 'success');
        }
    }, [addToast, persist]);

    const handleSaveAndRestart = useCallback(async () => {
        if (!(await persist())) return;
        if (pack?.status !== 'running') {
            addToast('Config saved. Pack is not running, so no restart was needed.', 'info');
            return;
        }
        try {
            await stopPack(packId);
            await startPack(packId);
            addToast('Config saved and server restarted.', 'success');
        } catch (error) {
            addToast(
                error instanceof Error
                    ? `Saved, but restart failed: ${error.message}`
                    : 'Saved, but restart failed.',
                'error',
            );
        }
    }, [addToast, pack?.status, packId, persist]);

    const handleBack = useCallback(() => {
        if (dirty && !window.confirm(UNSAVED_PROMPT)) return;
        void navigate('/home');
    }, [dirty, navigate]);

    const treeNode: ContainerNode | null = isContainer(model) ? model : null;
    const effectiveError = isValidPackId ? loadError : 'Invalid modpack id.';
    const showLoading = isValidPackId && isLoadingList;

    return (
        <main className="flex h-screen w-full flex-col px-4 py-4 text-slate-100 md:px-6">
            <header className="mb-4 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                    <button type="button" onClick={handleBack} className={btn('ghost', 'sm')}>
                        ← Back
                    </button>
                    <div>
                        <h1 className="text-xl font-bold text-white">Config editor</h1>
                        <p className="text-xs text-slate-400">
                            {pack ? pack.name : `Pack ${packIdParam}`}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={() => void handleSave()}
                        disabled={!dirty || isSaving || selectedPath === null}
                        className={btn('ghost', 'sm')}
                    >
                        {isSaving ? <CircularSpinner /> : 'Save'}
                    </button>
                    <button
                        type="button"
                        onClick={() => void handleSaveAndRestart()}
                        disabled={!dirty || isSaving || selectedPath === null}
                        className={btn('success', 'sm')}
                    >
                        Save & Restart
                    </button>
                </div>
            </header>

            {showLoading && (
                <div className="flex flex-1 items-center justify-center">
                    <CircularSpinner />
                </div>
            )}

            {!showLoading && effectiveError && (
                <p className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-300">
                    {effectiveError}
                </p>
            )}

            {!showLoading && !effectiveError && (
                <div className="grid min-h-0 flex-1 gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
                    <aside className="min-h-0 rounded-2xl border border-white/10 bg-slate-900/70 p-3">
                        <ConfigFileList
                            files={files}
                            selectedPath={selectedPath}
                            onSelect={openFile}
                        />
                    </aside>
                    <section className="min-h-0 overflow-auto rounded-2xl border border-white/10 bg-slate-900/70 p-4">
                        {selectedPath === null && (
                            <p className="text-sm text-slate-500">
                                Select a config file to view and edit it.
                            </p>
                        )}
                        {selectedPath !== null && isLoadingFile && (
                            <div className="flex items-center justify-center py-12">
                                <CircularSpinner />
                            </div>
                        )}
                        {selectedPath !== null && !isLoadingFile && (
                            <>
                                <div className="mb-3 flex items-center gap-2">
                                    <h2 className="font-mono text-sm text-slate-200">
                                        {selectedPath}
                                    </h2>
                                    {dirty && (
                                        <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-xs text-amber-300">
                                            unsaved
                                        </span>
                                    )}
                                </div>
                                {parseError ? (
                                    <div className="space-y-2">
                                        <p className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-xs text-amber-300">
                                            Could not parse as JSON ({parseError}). Editing raw text
                                            instead.
                                        </p>
                                        <textarea
                                            key={selectedPath}
                                            value={rawText}
                                            onChange={(event) => handleRawChange(event.target.value)}
                                            spellCheck={false}
                                            className="h-[70vh] w-full rounded-md bg-slate-950 p-3 font-mono text-xs text-slate-200 outline outline-1 outline-white/10 focus:outline-2 focus:outline-indigo-500"
                                        />
                                    </div>
                                ) : treeNode ? (
                                    <JsonTreeEditor
                                        key={selectedPath}
                                        node={treeNode}
                                        path={[]}
                                        onChangeValue={handleLeafChange}
                                    />
                                ) : (
                                    <p className="text-sm text-slate-500">
                                        This file has no editable content.
                                    </p>
                                )}
                            </>
                        )}
                    </section>
                </div>
            )}
        </main>
    );
};

export default ConfigEditor;
