import { memo } from 'react';

type McmsmLogoProps = {
    className?: string;
};

const McmsmLogo = ({ className = 'h-10 w-10' }: McmsmLogoProps) => (
    <svg className={className} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <rect width="32" height="32" rx="6" fill="#1e293b" />
        <path d="M6 22V10l5 6 5-6v12" stroke="#818cf8" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        <rect x="20" y="12" width="6" height="6" rx="1" fill="#34d399" opacity="0.9" />
        <rect x="20" y="20" width="6" height="4" rx="1" fill="#34d399" opacity="0.6" />
        <rect x="20" y="6" width="6" height="4" rx="1" fill="#34d399" opacity="0.6" />
    </svg>
);

export default memo(McmsmLogo);
