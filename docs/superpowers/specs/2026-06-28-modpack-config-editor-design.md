# Modpack Config Editor — Design

**Date:** 2026-06-28
**Status:** Approved (pending spec review)

## Goal

Let users scan, browse, read, and edit the JSON config files a Minecraft modpack
generates, from the web UI. Config files live under each pack's directory at:

- `modpacks/{packId}/config/*.json`
- `modpacks/{packId}/config/**/*.json` (a mod with multiple configs collects them in a subfolder)

## Decisions (locked during brainstorming)

| Topic | Decision |
|---|---|
| Editor depth | Tree view (collapsible key/value), **free-form text value inputs** — no type-specific widgets, no type assistance |
| Categorization | Group/label entries **by config file name**; folder-collected files nested under their folder |
| Parsing | **Comment-preserving** round-trip (JSONC). Comments shown to the user as inline UI hints |
| File types | `.json` only (includes JSONC-style with comments / trailing commas) |
| Surface | **Full page route** `/packs/:packId/config`, reached from the console (big) view |
| Edit while running | Allowed always; show "restart to apply" affordance via the Save & Restart button |
| Buttons | **Save** (gray) and **Save & Restart** (green); **Back** button top-left |

## Architecture — backend dumb, frontend smart

The backend **never parses JSON**. It scans paths and reads/writes raw file bytes.
All JSONC parsing, tree-building, comment extraction, and re-serialization live in
the frontend. This isolates the lossy-round-trip risk in one place and keeps the
backend a thin, security-focused file gateway.

### Data flow

1. Open config page → `GET /api/modpacks/{packId}/config/files` → backend walks
   `config/**/*.json`, returns relative paths + sizes.
2. Frontend categorizes the list by file name (folder-collected files nested).
3. Click a file → `GET .../config/file?path=<relative>` → backend returns raw text.
4. Frontend parses with a comment-preserving parser → renders tree; comments render
   as inline hints next to their key.
5. User edits free-form values → frontend re-serializes (comments intact).
6. **Save** → `PUT .../config/file?path=<relative>` with raw text body → backend writes bytes.
7. **Save & Restart** → PUT (save), then existing `POST .../stop` + `POST .../start`
   (only if the pack is currently running).

## Backend

New `ModPackConfigController`, base `/api/modpacks/{packId}/config`:

| Method | Path | Params / body | Returns |
|---|---|---|---|
| GET | `/files` | — | `List<ConfigFileDto>` |
| GET | `/file` | `?path=<relative>` | raw text, `text/plain; charset=utf-8` |
| PUT | `/file` | `?path=<relative>`, raw text body | `204 No Content` |

`ConfigFileDto` (record): `{ String relativePath, String fileName, long sizeBytes }`.

New `ModPackConfigService` (package `dk.mcmsm.services`):

- `List<ConfigFileDto> listConfigFiles(ModPack pack)` — resolve `config/` root,
  `Files.walk`, filter regular files with `.json` suffix (case-insensitive), return
  paths relative to `config/`. Returns empty list if no `config/` dir exists.
- `String readConfigFile(ModPack pack, String relPath)`
- `void writeConfigFile(ModPack pack, String relPath, String content)`

The controller loads the `ModPack` by `packId` via the existing `McModPackService`
(same pattern as `ModPackController`), then delegates to `ModPackConfigService`.

### Security (critical)

The `path` query param is attacker-controlled. Every read/write:

1. Resolves `relPath` against the pack's **`config/` root** (`modpacks/{packId}/config`),
   then `normalize()`.
2. Calls the path-traversal guard to confirm the resolved path stays within the
   `config/` root — rejects `../` escape, absolute paths, and symlink-out.
3. Enforces a `.json` suffix on both read and write — no reading/writing arbitrary files.
4. On violation: `400`/`404`, never leak absolute paths in the response.

**Refactor (justified):** `ensurePathWithinRoot` is currently `private` in
`ModPackFileService:549`. Extract it into a small shared util (e.g.
`dk.mcmsm.util.PathSafety.ensureWithinRoot(candidate, root, label)`) so both
`ModPackFileService` and the new `ModPackConfigService` use one proven
implementation instead of duplicating the guard. Update `ModPackFileService` to
call the extracted util.

Writes use try-with-resources + UTF-8 and are **atomic** (write to a temp file in
the same dir, then `Files.move(..., ATOMIC_MOVE/REPLACE_EXISTING)`) so a crash
mid-write cannot corrupt a live config.

## Frontend

Stack: React 19, react-router 7, Tailwind 4. No editor lib currently.

**New dependency:** `comment-json` — parses JSONC keeping comments as attached
symbols; `stringify` round-trips them. Fits a tree editor that mutates the parsed
object in place. (Alternative considered: `jsonc-parser` text-edit model — more
robust formatting preservation but heavier to drive from a tree UI. Chosen
`comment-json` for the object-model simplicity given free-form value editing.)

**Route:** add `<Route path="/packs/:packId/config" element={<ConfigEditor />} />`
to `App.tsx`.

**Components:**

- `pages/ConfigEditor.tsx` — page shell. Back button (top-left → navigate to `/home`
  or console view), pack name header, two-pane layout: file list (left) + editor (right).
  Owns: file list fetch, selected file, parsed model, dirty state, Save / Save & Restart.
  Unsaved-changes nav guard (block back/route change when dirty; confirm).
- `components/config/ConfigFileList.tsx` — categorized list keyed by file name;
  folder-collected files nested under folder label; search/filter box.
- `components/config/JsonTreeEditor.tsx` — recursive tree. Objects/arrays are
  collapsible; leaf values are **free-form text inputs**. Each key shows its
  associated comment (from the parser) as an inline hint. Reports edits up to the page.
- Buttons: **Save** (gray) writes the current file. **Save & Restart** (green) writes
  then, if the pack is running, stops + starts the container. Disabled when not dirty.

**API (`util/modpackApi.ts`):**

- `listConfigFiles(packId): Promise<ConfigFileDto[]>`
- `readConfigFile(packId, relPath): Promise<string>`
- `writeConfigFile(packId, relPath, content): Promise<void>`

**Entry point:** the console (big) view gets a **Configs** button that navigates to
`/packs/:packId/config`.

## Free-form value handling

Leaf values edited as plain text. On serialize, the frontend writes the edited text
back into the model. Because `comment-json` preserves the parsed structure and
comments, re-stringifying yields valid JSONC with comments intact. Values the user
types are stored as-entered (no forced type coercion, per the "don't care about
types" decision); the parser/serializer keeps the file syntactically valid.

## Error handling

- Parse failure on a file (malformed beyond JSONC tolerance): show an error state for
  that file in the editor pane; do not crash the page. Other files stay browsable.
- Backend read/write errors surface via the existing toast system.
- Save & Restart: if save succeeds but restart fails, report the restart error but keep
  the saved state (file is already written).

## Testing

Backend (`cd backend && mvn test`):
- `ModPackConfigService`: lists only `.json` under `config/`, recurses subfolders,
  returns relative paths; empty when no `config/` dir.
- Path-traversal guard: `../` escape, absolute path, non-`.json` suffix all rejected on
  read and write.
- Read returns exact bytes; write round-trips exact bytes (incl. comments — backend is
  byte-faithful).
- Extracted `PathSafety` util keeps `ModPackFileService` behavior unchanged.

Frontend:
- Categorization groups by file name; folder-collected nesting correct.
- JSONC parse → tree → serialize round-trip preserves comments.
- Dirty-state nav guard triggers on edit.

## Out of scope (YAGNI)

- Non-JSON configs (`.toml`, `.cfg`, `.properties`).
- Schema-aware forms / typed widgets / enum dropdowns.
- Mod-ownership inference for grouping.
- Diff/history/versioning of config edits.
- A dedicated backend restart endpoint (reuse stop + start).
