import BackendStatusIndicator from "./BackendStatusIndicator.tsx";
import {BackendStatus} from "../util/healthCheck.ts";


export default function StatusBox({text, status }: {text:string, status: BackendStatus }) {
    return (
        <div className="rounded-xl border border-white/10 bg-slate-800/70 px-4 py-3 text-sm text-slate-300">
            <h3 className="text-lg font-bold leading-none text-white">{text}</h3>
            <div className="mt-2 flex gap-2">
                <BackendStatusIndicator status={status} />
            </div>
        </div>
    );
}