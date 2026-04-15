type CloseButtonProps = {
    onClick: () => void;
    disabled?: boolean;
    className?: string;
};

const CloseButton = ({ onClick, disabled = false, className = '' }: CloseButtonProps) => {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            className={`inline-flex h-9 w-9 items-center justify-center rounded-full border border-white/10 bg-slate-800/80 text-slate-400 transition hover:border-red-500/40 hover:bg-red-950/60 hover:text-red-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500/50 disabled:opacity-40 ${className}`}
            aria-label="Close"
        >
            <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                <path d="M18 6 6 18" />
                <path d="M6 6l12 12" />
            </svg>
        </button>
    );
};

export default CloseButton;
