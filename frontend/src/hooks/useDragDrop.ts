import { useCallback, useEffect, useRef, useState } from 'react';

function isFileDragEvent(event: DragEvent): boolean {
    return event.dataTransfer?.types?.includes('Files') ?? false;
}

function getDroppedFile(event: DragEvent): File | null {
    return event.dataTransfer?.files?.[0] ?? null;
}

export function useDragDrop(onFileDrop: (file: File) => void) {
    const [isActive, setIsActive] = useState(false);
    const depthRef = useRef(0);
    const onFileDropRef = useRef(onFileDrop);
    onFileDropRef.current = onFileDrop;

    const handleDragOver = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'copy';
        }
    }, []);

    const handleDragEnter = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        depthRef.current += 1;
        setIsActive(true);
    }, []);

    const handleDragLeave = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        depthRef.current = Math.max(0, depthRef.current - 1);
        if (depthRef.current === 0) {
            setIsActive(false);
        }
    }, []);

    const handleDrop = useCallback((event: DragEvent) => {
        if (!isFileDragEvent(event)) {
            return;
        }

        event.preventDefault();
        depthRef.current = 0;
        setIsActive(false);

        const droppedFile = getDroppedFile(event);
        if (droppedFile) {
            onFileDropRef.current(droppedFile);
        }
    }, []);

    useEffect(() => {
        window.addEventListener('dragenter', handleDragEnter);
        window.addEventListener('dragover', handleDragOver);
        window.addEventListener('dragleave', handleDragLeave);
        window.addEventListener('drop', handleDrop);

        return () => {
            window.removeEventListener('dragenter', handleDragEnter);
            window.removeEventListener('dragover', handleDragOver);
            window.removeEventListener('dragleave', handleDragLeave);
            window.removeEventListener('drop', handleDrop);
        };
    }, [handleDragEnter, handleDragOver, handleDragLeave, handleDrop]);

    return { isActive };
}
