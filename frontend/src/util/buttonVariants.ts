export type ButtonVariant = 'primary' | 'danger' | 'ghost' | 'success' | 'warning';

const base = 'inline-flex items-center justify-center font-semibold transition disabled:opacity-50 disabled:cursor-not-allowed';

const variants: Record<ButtonVariant, string> = {
    primary: 'bg-indigo-600 text-white hover:bg-indigo-500',
    success: 'bg-emerald-600 text-white hover:bg-emerald-500',
    danger: 'bg-red-600 text-white hover:bg-red-500',
    warning: 'bg-amber-600 text-white hover:bg-amber-500',
    ghost: 'border border-white/20 bg-slate-800 text-white hover:bg-slate-700',
};

const sizes: Record<string, string> = {
    xs: 'rounded-md px-3 py-1.5 text-xs',
    sm: 'h-8 rounded-md px-3 text-sm',
    md: 'rounded-lg px-4 py-2 text-sm',
    icon: 'h-8 w-8 rounded-md',
    'icon-lg': 'h-10 w-10 rounded-md',
};

export function btn(variant: ButtonVariant, size: string = 'md'): string {
    return `${base} ${variants[variant]} ${sizes[size] ?? sizes.md}`;
}
