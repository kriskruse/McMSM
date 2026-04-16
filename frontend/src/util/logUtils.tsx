import type { ReactNode } from 'react';

export type LogLevel = 'info' | 'warn' | 'error' | 'default';
export type ParsedLine = { text: string; level: LogLevel };

export const LOG_LEVEL_CLASSES: Record<LogLevel, string> = {
    info: 'text-slate-200',
    warn: 'text-yellow-300',
    error: 'text-red-400',
    default: 'text-emerald-300',
};

export function parseLogLevel(line: string): LogLevel {
    if (line.includes('/INFO]') || line.includes('[INFO]') || line.includes(' INFO ')) return 'info';
    if (line.includes('/WARN]') || line.includes('[WARN]') || line.includes(' WARN ')) return 'warn';
    if (line.includes('/ERROR]') || line.includes('[ERROR]') || line.includes(' ERROR ') || line.includes('/FATAL]')) return 'error';
    return 'default';
}

export function parseLogLines(raw: string): ParsedLine[] {
    const lines = raw.split('\n');
    const result: ParsedLine[] = [];
    let lastLevel: LogLevel = 'default';

    for (const line of lines) {
        const detected = parseLogLevel(line);
        if (detected !== 'default') {
            lastLevel = detected;
            result.push({ text: line, level: detected });
        } else {
            // Stack traces / continuation lines inherit the previous level
            result.push({ text: line, level: line.trim() === '' ? 'default' : lastLevel });
        }
    }
    return result;
}

export function highlightText(text: string, query: string): ReactNode {
    if (!query) return text;
    const lower = text.toLowerCase();
    const lowerQ = query.toLowerCase();
    const parts: ReactNode[] = [];
    let last = 0;
    let idx = lower.indexOf(lowerQ, last);

    while (idx !== -1) {
        if (idx > last) parts.push(text.slice(last, idx));
        parts.push(
            <span key={idx} className="rounded-sm bg-yellow-500/30 px-0.5 text-yellow-200">
                {text.slice(idx, idx + query.length)}
            </span>,
        );
        last = idx + query.length;
        idx = lower.indexOf(lowerQ, last);
    }

    if (last < text.length) parts.push(text.slice(last));
    return parts.length > 0 ? parts : text;
}
