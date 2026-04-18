import { memo } from 'react';
import { useToast } from '../hooks/useToast';
import type { ToastVariant } from '../hooks/useToast';

const variantStyles: Record<ToastVariant, string> = {
    success: 'border-emerald-500/30 bg-emerald-950/80 text-emerald-200',
    error: 'border-red-500/30 bg-red-950/80 text-red-200',
    info: 'border-indigo-500/30 bg-indigo-950/80 text-indigo-200',
};

const ToastContainer = () => {
    const { toasts, removeToast } = useToast();

    if (toasts.length === 0) {
        return null;
    }

    return (
        <div className="fixed bottom-4 left-1/2 z-[60] flex -translate-x-1/2 flex-col gap-2" aria-live="polite">
            {toasts.map((toast) => (
                <div
                    key={toast.id}
                    className={`flex items-center gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg backdrop-blur-sm animate-[fadeSlideUp_0.25s_ease-out] ${variantStyles[toast.variant]}`}
                >
                    <span>{toast.message}</span>
                    <button
                        type="button"
                        onClick={() => removeToast(toast.id)}
                        className="ml-2 opacity-60 hover:opacity-100"
                        aria-label="Dismiss notification"
                    >
                        x
                    </button>
                </div>
            ))}
        </div>
    );
};

export default memo(ToastContainer);
