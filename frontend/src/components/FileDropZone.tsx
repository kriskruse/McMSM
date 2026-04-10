import { memo } from 'react';
import type { DragEvent } from 'react';

type FileDropZoneProps = {
    isDragging: boolean;
    selectedFileName: string | null;
    onDrop: (event: DragEvent<HTMLDivElement>) => void;
    onDragOver: (event: DragEvent<HTMLDivElement>) => void;
    onDragLeave: (event: DragEvent<HTMLDivElement>) => void;
};

const FileDropZone = ({ isDragging, selectedFileName, onDrop, onDragOver, onDragLeave }: FileDropZoneProps) => (
    <div
        onDrop={onDrop}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        className={`rounded-xl border-2 border-dashed p-8 text-center transition ${
            isDragging ? 'border-emerald-400 bg-emerald-500/10' : 'border-white/15 bg-slate-950/40'
        }`}
    >
        <p className="text-sm text-slate-300">Drop your modpack .zip file here</p>
        {selectedFileName && (
            <p className="mt-2 text-xs text-emerald-300">Selected: {selectedFileName}</p>
        )}
    </div>
);

export default memo(FileDropZone);
