import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { btn } from '../util/buttonVariants';

type ErrorBoundaryProps = {
    children: ReactNode;
};

type ErrorBoundaryState = {
    hasError: boolean;
    error: Error | null;
};

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
    constructor(props: ErrorBoundaryProps) {
        super(props);
        this.state = { hasError: false, error: null };
    }

    static getDerivedStateFromError(error: Error): ErrorBoundaryState {
        return { hasError: true, error };
    }

    componentDidCatch(error: Error, info: ErrorInfo): void {
        console.error('ErrorBoundary caught:', error, info);
    }

    render(): ReactNode {
        if (this.state.hasError) {
            return (
                <div className="flex min-h-screen items-center justify-center bg-slate-950 text-white">
                    <div className="max-w-md text-center">
                        <h1 className="text-2xl font-bold">Something went wrong</h1>
                        <p className="mt-2 text-sm text-slate-400">{this.state.error?.message}</p>
                        <button
                            type="button"
                            onClick={() => this.setState({ hasError: false, error: null })}
                            className={`mt-4 ${btn('primary')}`}
                        >
                            Try again
                        </button>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
