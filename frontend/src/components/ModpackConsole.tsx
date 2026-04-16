import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { ModPackCardDto } from '../dto';
import { getPackLogs } from '../util/modpackApi';
import { LOG_LEVEL_CLASSES, highlightText, parseLogLines } from '../util/logUtils';
import { resolveIndicator } from '../util/statusIndicator';
import CommandBar from './CommandBar';
import MetadataTag from './MetadataTag';
import StatusBadge from './StatusBadge';

type ModpackConsoleProps = {
    modpack: ModPackCardDto;
};

const LOG_POLL_MS = 2_000;
const MAX_SSE_LINES = 5_000;

const ModpackConsole = ({ modpack }: ModpackConsoleProps) => {
    const [logs, setLogs] = useState('Loading logs...');
    const [logError, setLogError] = useState('');
    const [autoScroll, setAutoScroll] = useState(true);
    const [tailCount, setTailCount] = useState(200);
    const [copied, setCopied] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const logContainerRef = useRef<HTMLDivElement>(null);
    const autoScrollRef = useRef(true);
    const sseBufferRef = useRef('');
    const sseRafRef = useRef(0);
    const isProgrammaticScrollRef = useRef(false);

    const handleCopyLogs = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(logs);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {
            // clipboard API unavailable
        }
    }, [logs]);

    const handleScroll = useCallback(() => {
        if (isProgrammaticScrollRef.current) return;
        const el = logContainerRef.current;
        if (!el) return;
        const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
        if (atBottom !== autoScrollRef.current) {
            autoScrollRef.current = atBottom;
            setAutoScroll(atBottom);
        }
    }, []);

    useEffect(() => {
        let isMounted = true;
        let eventSource: EventSource | null = null;
        let pollTimer: number | undefined;

        const startPolling = () => {
            const loadLogs = async () => {
                try {
                    const nextLogs = await getPackLogs(modpack.packId, tailCount);
                    if (!isMounted) return;
                    setLogs(nextLogs || 'No logs available yet.');
                    setLogError('');
                } catch (error) {
                    if (!isMounted) return;
                    setLogError(error instanceof Error ? error.message : 'Failed to load logs.');
                }
            };
            void loadLogs();
            pollTimer = window.setInterval(() => void loadLogs(), LOG_POLL_MS);
        };

        const flushSseBuffer = () => {
            sseRafRef.current = 0;
            const chunk = sseBufferRef.current;
            if (!chunk) return;
            sseBufferRef.current = '';
            setLogs((prev) => {
                const next = prev + chunk;
                const lines = next.split('\n');
                if (lines.length > MAX_SSE_LINES) {
                    return lines.slice(lines.length - MAX_SSE_LINES).join('\n');
                }
                return next;
            });
            setLogError('');
        };

        const startSSE = () => {
            setLogs('');
            sseBufferRef.current = '';
            eventSource = new EventSource(`/api/modpacks/${modpack.packId}/logs/stream?tail=${tailCount}`);

            eventSource.addEventListener('log', (event: MessageEvent) => {
                if (!isMounted) return;
                sseBufferRef.current += event.data;
                if (!sseRafRef.current) {
                    sseRafRef.current = requestAnimationFrame(flushSseBuffer);
                }
            });

            eventSource.onerror = () => {
                if (!isMounted) return;
                eventSource?.close();
                eventSource = null;
                startPolling();
            };
        };

        if (modpack.status === 'running') {
            startSSE();
        } else {
            startPolling();
        }

        return () => {
            isMounted = false;
            eventSource?.close();
            if (pollTimer !== undefined) window.clearInterval(pollTimer);
            if (sseRafRef.current) cancelAnimationFrame(sseRafRef.current);
        };
    }, [modpack.packId, modpack.status, tailCount]);

    const metadataTags = useMemo(
        () => [
            { label: 'Pack ID', value: String(modpack.packId) },
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

    const parsedLines = useMemo(() => parseLogLines(logs), [logs]);

    const rowVirtualizer = useVirtualizer({
        count: parsedLines.length,
        getScrollElement: () => logContainerRef.current,
        estimateSize: () => 20,
        overscan: 50,
    });

    useEffect(() => {
        if (!autoScrollRef.current || parsedLines.length === 0) return;
        isProgrammaticScrollRef.current = true;
        rowVirtualizer.scrollToIndex(parsedLines.length - 1, { align: 'end' });
        requestAnimationFrame(() => {
            isProgrammaticScrollRef.current = false;
        });
    }, [parsedLines, rowVirtualizer]);

    const indicator = resolveIndicator(modpack);
    const trimmedSearch = searchQuery.trim();
    const searchMatchCount = useMemo(() => {
        if (!trimmedSearch) return 0;
        const q = trimmedSearch.toLowerCase();
        return parsedLines.filter((l) => l.text.toLowerCase().includes(q)).length;
    }, [parsedLines, trimmedSearch]);

    return (
        <div>
            <h4 className="mb-3 flex items-center gap-3 text-sm font-semibold text-white">
                {modpack.name}
                <StatusBadge indicator={indicator} />
            </h4>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
                <div className="relative rounded-lg border border-white/10 bg-slate-950 p-3">
                    <div className="mb-2 flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                            <p className="text-xs text-slate-400">Docker logs</p>
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Search..."
                                className="w-40 rounded-md border border-white/10 bg-slate-950 px-2 py-0.5 text-xs text-slate-200 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none"
                            />
                            {trimmedSearch && (
                                <span className="text-xs text-slate-500">
                                    {searchMatchCount} match{searchMatchCount !== 1 ? 'es' : ''}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-3">
                            <span className="text-xs text-slate-500">Last {tailCount} lines</span>
                            {tailCount < 5000 && (
                                <button
                                    type="button"
                                    onClick={() => setTailCount((prev) => Math.min(prev + 200, 5000))}
                                    className="text-xs text-emerald-400 transition hover:text-emerald-300"
                                >
                                    Load more
                                </button>
                            )}
                            <button
                                type="button"
                                onClick={handleCopyLogs}
                                className="text-xs text-slate-400 transition hover:text-white"
                            >
                                {copied ? 'Copied!' : 'Copy'}
                            </button>
                        </div>
                    </div>
                    <div
                        ref={logContainerRef}
                        onScroll={handleScroll}
                        className="h-[68vh] overflow-auto font-mono text-xs"
                    >
                        <div
                            style={{
                                height: rowVirtualizer.getTotalSize(),
                                width: '100%',
                                position: 'relative',
                            }}
                        >
                            {rowVirtualizer.getVirtualItems().map((virtualRow) => {
                                const line = parsedLines[virtualRow.index];
                                const matches = trimmedSearch
                                    ? line.text.toLowerCase().includes(trimmedSearch.toLowerCase())
                                    : true;
                                return (
                                    <div
                                        key={virtualRow.index}
                                        style={{
                                            position: 'absolute',
                                            top: 0,
                                            left: 0,
                                            width: '100%',
                                            height: virtualRow.size,
                                            transform: `translateY(${virtualRow.start}px)`,
                                        }}
                                        className={`whitespace-pre ${LOG_LEVEL_CLASSES[line.level]}${trimmedSearch && !matches ? ' opacity-25' : ''}`}
                                    >
                                        {trimmedSearch && matches
                                            ? highlightText(line.text, trimmedSearch)
                                            : line.text || '\u00A0'}
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                    {!autoScroll && (
                        <button
                            type="button"
                            onClick={() => {
                                autoScrollRef.current = true;
                                setAutoScroll(true);
                                isProgrammaticScrollRef.current = true;
                                rowVirtualizer.scrollToIndex(parsedLines.length - 1, { align: 'end' });
                                requestAnimationFrame(() => {
                                    isProgrammaticScrollRef.current = false;
                                });
                            }}
                            className="absolute bottom-14 right-6 rounded-full border border-white/10 bg-slate-700 px-3 py-1.5 text-xs text-white shadow-lg transition hover:bg-slate-600"
                        >
                            Scroll to bottom
                        </button>
                    )}
                    <CommandBar packId={modpack.packId} disabled={modpack.status !== 'running'} />
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

export default memo(ModpackConsole);
