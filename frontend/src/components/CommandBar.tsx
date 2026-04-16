import { memo, useCallback, useRef, useState } from 'react';
import { sendCommand } from '../util/modpackApi';

type CommandBarProps = {
    packId: number;
    disabled: boolean;
};

const CommandBar = ({ packId, disabled }: CommandBarProps) => {
    const [commandInput, setCommandInput] = useState('');
    const [commandError, setCommandError] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);

    const handleSubmit = useCallback(
        async (e: React.FormEvent) => {
            e.preventDefault();
            const trimmed = commandInput.trim();
            if (!trimmed) return;
            setCommandError('');
            try {
                await sendCommand(packId, trimmed);
                setCommandInput('');
            } catch (err) {
                setCommandError(err instanceof Error ? err.message : 'Failed to send command.');
            }
        },
        [packId, commandInput],
    );

    return (
        <>
            <form onSubmit={handleSubmit} className="mt-2 flex items-center gap-2">
                <span className="text-sm text-slate-500">{'>'}</span>
                <input
                    ref={inputRef}
                    type="text"
                    value={commandInput}
                    onChange={(e) => setCommandInput(e.target.value)}
                    placeholder="Enter server command..."
                    disabled={disabled}
                    className="flex-1 rounded-md border border-white/10 bg-slate-950 px-3 py-1.5 font-mono text-xs text-slate-200 placeholder:text-slate-500 focus:border-emerald-500 focus:outline-none disabled:opacity-40"
                />
                <button
                    type="submit"
                    disabled={disabled || !commandInput.trim()}
                    className="rounded-md bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-emerald-500 disabled:opacity-40"
                >
                    Send
                </button>
            </form>
            {commandError && <p className="mt-1 text-xs text-red-400">{commandError}</p>}
        </>
    );
};

export default memo(CommandBar);
