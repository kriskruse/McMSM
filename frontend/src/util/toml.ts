import { parse, stringify } from 'smol-toml';

export type TomlModel = unknown;

/** Parses TOML text into a plain object model. Throws on invalid input. */
export function parseToml(text: string): TomlModel {
    return parse(text) as TomlModel;
}

/** Serializes a TOML model back to text. */
export function stringifyToml(model: TomlModel): string {
    return stringify(model as Parameters<typeof stringify>[0]);
}

