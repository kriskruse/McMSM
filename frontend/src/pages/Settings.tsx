import { useEffect, useState } from 'react';
import { NavLink } from 'react-router';
import CircularSpinner from '../components/CircularSpinner';
import { useToast } from '../hooks/useToast';
import { btn } from '../util/buttonVariants';
import { getSettings, updateSettings } from '../util/settingsApi';
import { INPUT_CLASS } from '../util/styles';

const Settings = () => {
    const { addToast } = useToast();
    const [curseforgeApiKeyConfigured, setCurseforgeApiKeyConfigured] = useState(false);
    const [curseforgeApiKeyInput, setCurseforgeApiKeyInput] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [loadError, setLoadError] = useState('');

    useEffect(() => {
        let cancelled = false;
        getSettings()
            .then((settings) => {
                if (cancelled) return;
                setCurseforgeApiKeyConfigured(settings.curseforgeApiKeyConfigured);
                setLoadError('');
            })
            .catch((err: unknown) => {
                if (cancelled) return;
                setLoadError(err instanceof Error ? err.message : 'Failed to load settings');
            })
            .finally(() => {
                if (!cancelled) setIsLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, []);

    const handleSaveCurseforgeKey = async () => {
        if (!curseforgeApiKeyInput) {
            addToast('Enter a key before saving.', 'error');
            return;
        }
        setIsSaving(true);
        try {
            const updated = await updateSettings({ curseforgeApiKey: curseforgeApiKeyInput });
            setCurseforgeApiKeyConfigured(updated.curseforgeApiKeyConfigured);
            setCurseforgeApiKeyInput('');
            addToast('CurseForge API key saved.', 'success');
        } catch (err) {
            addToast(err instanceof Error ? err.message : 'Failed to save settings', 'error');
        } finally {
            setIsSaving(false);
        }
    };

    const handleClearCurseforgeKey = async () => {
        setIsSaving(true);
        try {
            const updated = await updateSettings({ curseforgeApiKey: '' });
            setCurseforgeApiKeyConfigured(updated.curseforgeApiKeyConfigured);
            addToast('CurseForge API key cleared.', 'success');
        } catch (err) {
            addToast(err instanceof Error ? err.message : 'Failed to clear key', 'error');
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <main className="w-full max-w-3xl px-4 py-6 text-slate-100 md:px-6">
            <header className="mb-6 flex flex-col gap-2 rounded-2xl border border-white/10 bg-slate-900/75 p-5 md:flex-row md:items-center md:justify-between">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-white md:text-3xl">Settings</h1>
                    <p className="mt-1 text-sm text-slate-400">
                        Application configuration. Secrets are encrypted on disk.
                    </p>
                </div>
                <NavLink to="/home" className={btn('ghost')}>
                    Back
                </NavLink>
            </header>

            {isLoading && (
                <div className="flex items-center justify-center py-12">
                    <CircularSpinner />
                </div>
            )}

            {!isLoading && loadError && (
                <p className="rounded-md border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-300">
                    {loadError}
                </p>
            )}

            {!isLoading && !loadError && (
                <section className="rounded-2xl border border-white/10 bg-slate-900/70 p-5">
                    <h2 className="mb-1 text-lg font-semibold text-white">Integrations</h2>
                    <p className="mb-4 text-xs text-slate-400">
                        API keys and credentials for external services.
                    </p>

                    <div className="space-y-3">
                        <div>
                            <label
                                htmlFor="curseforge-key"
                                className="block text-sm font-medium text-gray-100"
                            >
                                CurseForge API key
                            </label>
                            <p className="mt-0.5 text-xs text-slate-400">
                                Used to fetch modpack metadata from the CurseForge Core API.
                            </p>
                            <p className="mt-1 text-xs">
                                Status:{' '}
                                <span
                                    className={
                                        curseforgeApiKeyConfigured
                                            ? 'font-semibold text-emerald-300'
                                            : 'font-semibold text-amber-300'
                                    }
                                >
                                    {curseforgeApiKeyConfigured ? 'configured' : 'not configured'}
                                </span>
                            </p>
                            <input
                                id="curseforge-key"
                                type="password"
                                autoComplete="off"
                                placeholder={
                                    curseforgeApiKeyConfigured
                                        ? 'Enter a new key to replace the stored one'
                                        : 'Paste your CurseForge API key'
                                }
                                value={curseforgeApiKeyInput}
                                onChange={(e) => setCurseforgeApiKeyInput(e.target.value)}
                                className={INPUT_CLASS}
                                disabled={isSaving}
                            />
                            <div className="mt-3 flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => {
                                        void handleSaveCurseforgeKey();
                                    }}
                                    className={btn('primary')}
                                    disabled={isSaving || !curseforgeApiKeyInput}
                                >
                                    {isSaving ? <CircularSpinner /> : 'Save'}
                                </button>
                                {curseforgeApiKeyConfigured && (
                                    <button
                                        type="button"
                                        onClick={() => {
                                            void handleClearCurseforgeKey();
                                        }}
                                        className={btn('danger')}
                                        disabled={isSaving}
                                    >
                                        Clear
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                </section>
            )}
        </main>
    );
};

export default Settings;
