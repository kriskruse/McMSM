import { memo, useState } from 'react';
import { commentFor, formatLeaf, isContainer, type ContainerNode } from '../../util/jsonc';

type JsonTreeEditorProps = {
    node: ContainerNode;
    path: string[];
    onChangeValue: (path: string[], rawText: string) => void;
};

type TreeRowProps = {
    parent: ContainerNode;
    keyName: string;
    path: string[];
    onChangeValue: (path: string[], rawText: string) => void;
};

const CommentHint = ({ comment }: { comment: string }) => {
    if (!comment) {
        return null;
    }
    return <p className="mb-1 whitespace-pre-wrap text-xs italic text-emerald-300/70">{comment}</p>;
};

const TreeRow = ({ parent, keyName, path, onChangeValue }: TreeRowProps) => {
    const value = parent[keyName];
    const comment = commentFor(parent, keyName);
    const childPath = [...path, keyName];
    const [collapsed, setCollapsed] = useState(false);

    if (isContainer(value)) {
        const childKeys = Object.keys(value);
        const summary = Array.isArray(value) ? `[${childKeys.length}]` : `{${childKeys.length}}`;
        return (
            <div className="border-l border-white/10 pl-3">
                <CommentHint comment={comment} />
                <button
                    type="button"
                    onClick={() => setCollapsed((prev) => !prev)}
                    className="flex items-center gap-2 py-1 text-sm font-semibold text-slate-200 hover:text-white"
                >
                    <span className="text-slate-500">{collapsed ? '▶' : '▼'}</span>
                    <span>{keyName}</span>
                    <span className="text-xs font-normal text-slate-500">{summary}</span>
                </button>
                {!collapsed && (
                    <div className="ml-2">
                        {childKeys.map((childKey) => (
                            <TreeRow
                                key={childKey}
                                parent={value}
                                keyName={childKey}
                                path={childPath}
                                onChangeValue={onChangeValue}
                            />
                        ))}
                    </div>
                )}
            </div>
        );
    }

    const inputId = `cfg-${childPath.join('-')}`;
    return (
        <div className="py-1.5">
            <label htmlFor={inputId} className="block text-sm font-medium text-slate-300">
                {keyName}
            </label>
            <CommentHint comment={comment} />
            <input
                id={inputId}
                type="text"
                defaultValue={formatLeaf(value)}
                onChange={(event) => onChangeValue(childPath, event.target.value)}
                className="mt-0.5 block w-full rounded-md bg-white/5 px-3 py-1.5 font-mono text-sm text-white outline outline-white/10 focus:outline-2 focus:outline-indigo-500"
            />
        </div>
    );
};

const JsonTreeEditor = ({ node, path, onChangeValue }: JsonTreeEditorProps) => {
    const keys = Object.keys(node);
    if (keys.length === 0) {
        return <p className="text-sm text-slate-500">This file has no editable values.</p>;
    }
    return (
        <div className="space-y-0.5">
            {keys.map((key) => (
                <TreeRow key={key} parent={node} keyName={key} path={path} onChangeValue={onChangeValue} />
            ))}
        </div>
    );
};

export default memo(JsonTreeEditor);
