import { createContext, useCallback, useContext, useRef, useState } from 'react';
import type { ReactNode } from 'react';

export type ToastVariant = 'success' | 'error' | 'info';

type Toast = {
    id: number;
    message: string;
    variant: ToastVariant;
};

type ToastContextValue = {
    toasts: Toast[];
    addToast: (message: string, variant?: ToastVariant) => void;
    removeToast: (id: number) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

const TOAST_DURATION_MS = 4000;

export function ToastProvider({ children }: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const nextId = useRef(0);

    const removeToast = useCallback((id: number) => {
        setToasts((prev) => prev.filter((t) => t.id !== id));
    }, []);

    const addToast = useCallback((message: string, variant: ToastVariant = 'success') => {
        const id = nextId.current++;
        setToasts((prev) => [...prev, { id, message, variant }]);
        window.setTimeout(() => removeToast(id), TOAST_DURATION_MS);
    }, [removeToast]);

    return (
        <ToastContext value={{ toasts, addToast, removeToast }}>
            {children}
        </ToastContext>
    );
}

export function useToast() {
    const ctx = useContext(ToastContext);
    if (!ctx) {
        throw new Error('useToast must be used within a ToastProvider');
    }
    return ctx;
}
