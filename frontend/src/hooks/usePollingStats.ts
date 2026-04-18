import { useEffect, useRef, useState } from 'react';

export function usePollingStats<T>(
    fetcher: () => Promise<T | null>,
    intervalMs: number,
    enabled: boolean,
): T | null {
    const [value, setValue] = useState<T | null>(null);
    const fetcherRef = useRef(fetcher);
    fetcherRef.current = fetcher;

    useEffect(() => {
        if (!enabled) {
            setValue(null);
            return;
        }

        let cancelled = false;

        const tick = async () => {
            try {
                const next = await fetcherRef.current();
                if (!cancelled) {
                    setValue(next);
                }
            } catch {
                if (!cancelled) {
                    setValue(null);
                }
            }
        };

        void tick();
        const timerId = window.setInterval(() => {
            void tick();
        }, intervalMs);

        return () => {
            cancelled = true;
            window.clearInterval(timerId);
        };
    }, [enabled, intervalMs]);

    return value;
}
