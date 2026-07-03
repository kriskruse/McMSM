import { parse, stringify } from 'comment-json';

/**
 * Helpers around comment-json so the config tree editor can parse JSON(C),
 * surface comments as UI hints, edit leaf values, and re-serialize without
 * losing comments or formatting.
 *
 * The parsed model is a live object whose comments live in Symbol-keyed
 * properties (`before:<key>` / `after:<key>`). Mutating existing string keys
 * preserves those comment symbols, so we edit the model in place and stringify.
 */

export type JsonModel = unknown;

const INDENT = 2;

/** Parses JSON(C) text into a comment-preserving model. Throws on invalid input. */
export function parseJsonc(text: string): JsonModel {
    return parse(text) as JsonModel;
}

/** Serializes a model back to JSON(C) text, preserving comments and 2-space indent. */
export function stringifyJsonc(model: JsonModel): string {
    return stringify(model, null, INDENT);
}

/** A node that can hold child keys (object or array) in the editable tree. */
export type ContainerNode = Record<string, unknown>;

/** Returns true when the value is an editable container (object or array). */
export function isContainer(value: unknown): value is ContainerNode {
    return typeof value === 'object' && value !== null;
}

/**
 * Collects the comment text attached to a key on a node, joined by newlines.
 * Reads both leading (`before:`) and trailing (`after:`) comments.
 */
export function commentFor(node: unknown, key: string): string {
    if (!isContainer(node)) {
        return '';
    }
    const wanted = [`before:${key}`, `after:${key}`];
    const parts: string[] = [];
    for (const symbol of Object.getOwnPropertySymbols(node)) {
        if (!symbol.description || !wanted.includes(symbol.description)) {
            continue;
        }
        const tokens = (node as Record<symbol, unknown>)[symbol];
        if (!Array.isArray(tokens)) {
            continue;
        }
        for (const token of tokens) {
            const value = (token as { value?: unknown })?.value;
            if (typeof value === 'string' && value.trim()) {
                parts.push(value.trim());
            }
        }
    }
    return parts.join('\n');
}

/** Renders a primitive leaf value as editable text. */
export function formatLeaf(value: unknown): string {
    return typeof value === 'string' ? value : JSON.stringify(value);
}

/**
 * Converts edited text back into a JSON value. Text that parses as a JSON
 * primitive (number, boolean, null) becomes that primitive; everything else is
 * kept as a string. Matches the "free-form input, no type assistance" design.
 */
export function coerceLeaf(text: string): unknown {
    const trimmed = text.trim();
    if (trimmed === '') {
        return text;
    }
    if (trimmed === 'true' || trimmed === 'false' || trimmed === 'null') {
        return JSON.parse(trimmed);
    }
    if (/^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(trimmed)) {
        return Number(trimmed);
    }
    return text;
}

/**
 * Sets a leaf value at the given key path on the model, coercing the raw text.
 * Mutates the model in place; comment symbols on the parent are preserved.
 */
export function setLeafAtPath(model: JsonModel, path: string[], rawText: string): void {
    if (path.length === 0) {
        return;
    }
    let parent = model;
    for (let i = 0; i < path.length - 1; i += 1) {
        if (!isContainer(parent)) {
            return;
        }
        parent = parent[path[i]];
    }
    if (isContainer(parent)) {
        parent[path[path.length - 1]] = coerceLeaf(rawText);
    }
}
