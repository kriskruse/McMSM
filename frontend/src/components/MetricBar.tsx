import { memo } from 'react';

type Fraction = { numerator: string; denominator: string };

type MetricBarProps = {
    label: string;
    percent: number;
    displayValue?: string;
    fraction?: Fraction;
};

function colorFor(percent: number): string {
    if (percent >= 90) return 'bg-red-500';
    if (percent >= 70) return 'bg-amber-500';
    return 'bg-emerald-500';
}

const MetricBar = ({ label, percent, displayValue, fraction }: MetricBarProps) => {
    const clamped = Math.max(0, Math.min(100, percent));

    const bar = (
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
            <div
                className={`h-full rounded-full transition-all duration-300 ${colorFor(clamped)}`}
                style={{ width: `${clamped}%` }}
            />
        </div>
    );

    if (fraction) {
        return (
            <div className="flex items-center gap-2">
                <div className="min-w-0 flex-1">
                    <div className="mb-1 text-xs text-slate-400">{label}</div>
                    {bar}
                </div>
                <div className="flex flex-col items-end font-mono text-[10px] leading-tight text-slate-200">
                    <span>{fraction.numerator}</span>
                    <span className="my-0.5 block h-px w-full bg-slate-600" />
                    <span className="text-slate-400">{fraction.denominator}</span>
                </div>
            </div>
        );
    }

    return (
        <div>
            <div className="mb-1 flex items-center justify-between text-xs">
                <span className="text-slate-400">{label}</span>
                <span className="font-mono text-slate-200">
                    {displayValue ?? `${clamped.toFixed(1)}%`}
                </span>
            </div>
            {bar}
        </div>
    );
};

export default memo(MetricBar);
