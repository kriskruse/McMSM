import { useMemo, useState } from 'react';
import type { ConfigFileDto } from '../../dto';

type ConfigFileListProps = {
    files: ConfigFileDto[];
    selectedPath: string | null;
    onSelect: (relativePath: string) => void;
};

type Grouped = {
    rootFiles: ConfigFileDto[];
    folders: Map<string, ConfigFileDto[]>;
};

function groupFiles(files: ConfigFileDto[]): Grouped {
    const rootFiles: ConfigFileDto[] = [];
    const folders = new Map<string, ConfigFileDto[]>();
    for (const file of files) {
        const slash = file.relativePath.indexOf('/');
        if (slash === -1) {
            rootFiles.push(file);
            continue;
        }
        const folder = file.relativePath.slice(0, slash);
        const bucket = folders.get(folder) ?? [];
        bucket.push(file);
        folders.set(folder, bucket);
    }
    return { rootFiles, folders };
}

const FileButton = ({
    file,
    selected,
    onSelect,
}: {
    file: ConfigFileDto;
    selected: boolean;
    onSelect: (relativePath: string) => void;
}) => (
    <button
        type="button"
        onClick={() => onSelect(file.relativePath)}
        title={file.relativePath}
        className={`block w-full truncate rounded-md px-2 py-1 text-left text-sm transition ${
            selected
                ? 'bg-indigo-600/30 text-white'
                : 'text-slate-300 hover:bg-white/5 hover:text-white'
        }`}
    >
        {file.fileName}
    </button>
);

const ConfigFileList = ({ files, selectedPath, onSelect }: ConfigFileListProps) => {
    const [query, setQuery] = useState('');

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) {
            return files;
        }
        return files.filter((file) => file.relativePath.toLowerCase().includes(q));
    }, [files, query]);

    const { rootFiles, folders } = useMemo(() => groupFiles(filtered), [filtered]);

    return (
        <div className="flex h-full flex-col">
            <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search configs..."
                className="mb-3 w-full rounded-md bg-white/5 px-3 py-1.5 text-sm text-white outline outline-1 outline-white/10 placeholder:text-slate-500 focus:outline-2 focus:outline-indigo-500"
            />
            <div className="flex-1 overflow-auto pr-1">
                {filtered.length === 0 && (
                    <p className="px-2 py-1 text-sm text-slate-500">No config files found.</p>
                )}
                {rootFiles.map((file) => (
                    <FileButton
                        key={file.relativePath}
                        file={file}
                        selected={file.relativePath === selectedPath}
                        onSelect={onSelect}
                    />
                ))}
                {[...folders.entries()].map(([folder, folderFiles]) => (
                    <div key={folder} className="mt-2">
                        <p className="px-2 py-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                            {folder}
                        </p>
                        <div className="border-l border-white/10 pl-2">
                            {folderFiles.map((file) => (
                                <FileButton
                                    key={file.relativePath}
                                    file={file}
                                    selected={file.relativePath === selectedPath}
                                    onSelect={onSelect}
                                />
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default ConfigFileList;
