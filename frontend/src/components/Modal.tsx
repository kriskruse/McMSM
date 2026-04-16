import type { ReactNode } from 'react';
import { memo } from 'react';
import CloseButton from './CloseButton';

type ModalProps = {
    maxWidth?: string;
    title: string;
    subtitle?: string;
    onClose: () => void;
    closeDisabled?: boolean;
    children: ReactNode;
};

const Modal = ({ maxWidth = 'max-w-2xl', title, subtitle, onClose, closeDisabled = false, children }: ModalProps) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4">
        <div className={`w-full ${maxWidth} rounded-2xl border border-white/10 bg-slate-900 p-6 shadow-2xl`}>
            <div className="mb-5 flex items-start justify-between gap-4">
                <div>
                    <h2 className="text-xl font-semibold text-white">{title}</h2>
                    {subtitle && <p className="mt-1 text-sm text-slate-400">{subtitle}</p>}
                </div>
                <CloseButton onClick={onClose} disabled={closeDisabled} className="shrink-0" />
            </div>
            {children}
        </div>
    </div>
);

export default memo(Modal);
