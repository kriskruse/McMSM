# McMSM Full Project Audit: Improvements & Feature Ideas

## Context

Full codebase audit of McMSM — a self-hosted Minecraft modpack server manager. User=host, so security is out of scope. Covers code quality, performance, tech stack upgrades, UI/UX, infrastructure, distribution, and feature opportunities. Refined with web research on current best practices (2025-2026).

---

## 1. TECH STACK UPGRADES (Quick Wins)

| # | Change | Effort | Impact | Detail |
|---|--------|--------|--------|--------|
| T1 | **Spring Boot 4.1.0-M2 → 4.0.1 GA** | Low | High | Milestone version not production-ready. 4.0.1 includes 88 bug fixes. Stable foundation |
| T2 | **Enable virtual threads** | 1 line | High | Add `spring.threads.virtual.enabled=true`. ~43% less memory, 4x better tail latency for concurrent SSE streams, uploads, Docker ops |
| T3 | **Vite 6.2 → 8** | Low | Medium | Rolldown (Rust bundler) replaces Rollup+esbuild. 10-30x faster builds, 100x less peak memory. Drop-in for simple configs |
| T4 | **React Router 6.19 → v7** | Low | Medium | ~15% smaller bundle, built-in route error boundaries, better TypeScript. `react-router-dom` package deprecated |
| T5 | **React Compiler** | Low | Medium | `babel-plugin-react-compiler` auto-memoizes. Eliminates ~10 manual `useCallback`/`useMemo` in `useModpacks.ts` |
| T6 | **docker-java 3.7.0 → 3.7.1** | Low | Low | Security patches in transitive deps (Netty, Jackson, commons). Also increase `responseTimeout` from 500ms → 5s — 500ms too aggressive for image pulls |
| T7 | **Adopt `useOptimistic`** (React 19) | Medium | Medium | Instant UI feedback on deploy/start/stop. Update status optimistically, revert on failure. Dashboard feels snappier |
| T8 | **Adopt `useActionState`** (React 19) | Medium | Medium | Replace manual loading/error state tracking for pack actions. Less boilerplate in `useModpacks.ts` |

---

## 2. CODE QUALITY IMPROVEMENTS

### Backend

| # | Area | Issue | Suggestion |
|---|------|-------|------------|
| C1 | **God object** | `ModPackFileService.java` (628 lines, 12+ responsibilities) | Split into: `TemplateService`, `MetadataInferenceService`, `MemoryCalculationService`, file ops |
| C2 | **God object** | `ContainerService.java` (493 lines) | Extract command execution, log streaming, inspection into separate classes |
| C3 | **Duplicate repo logic** | `FileUserRepository` + `FileModPackRepository` | Nearly identical `save()`, `nextId()`. Extract generic base or utility |
| C4 | **Exception handling gaps** | `GlobalExceptionHandler.java` | Missing handlers for `MultipartException`, `HttpMessageNotReadableException`, generic `Exception` fallback |
| C5 | **Docker client coupling** | `ContainerService.java:43-50` | DockerClient created in constructor. Should be injected as a Spring Bean for testability |
| C6 | **IOException wrapping** | `ModPackFileService.java:112-114` | IOException → generic IllegalStateException. Loses type info. Use domain exceptions |
| C7 | **ModPack entity** | `ModPack.java` — mutable POJO with 17 fields | Split into immutable `ModPackConfig` record (name, version, javaVersion, port, entryPoint) + mutable `ModPackState` (status, containerId, isDeployed) |
| C8 | **Java 25 features** | Throughout backend | Adopt module imports (`import module java.base`), Stream Gatherers for container iteration, sealed interfaces for result types |

### Frontend

| # | Area | Issue | Suggestion |
|---|------|-------|------------|
| C9 | **Duplicated input classes** | `Login.tsx:40`, `Register.tsx:44`, `ModpackMetadataModal.tsx:139` | Extract to shared Tailwind class constant |
| C10 | **Large components** | `ModpackConsole.tsx` (386 lines), `Home.tsx` (237 lines) | Split console into LogViewer, CommandInput, MetadataTags subcomponents |
| C11 | **No auth context** | Throughout frontend | No `useAuth()` hook or AuthContext. Auth state implicit from routing |
| C12 | **Duplicated modal pattern** | Overlay + box + close across 3 modals | Extract reusable `<Modal>` wrapper component |
| C13 | **Status indicator duplication** | `statusIndicator.ts` vs `BackendStatusIndicator.tsx` | Redundant style definitions. Single source of truth |
| C14 | **Placeholder logo** | `Login.tsx:46`, `Register.tsx:50` | Using Tailwind marketing asset URL. Replace with project logo or remove |

---

## 3. PERFORMANCE IMPROVEMENTS

| # | Area | Issue | Fix |
|---|------|-------|-----|
| P1 | **Full JSON read on every op** | `JsonFileStore.readAll()` deserializes entire file per API call | Add in-memory cache invalidated on write. Or consider SQLite if feature set grows (audit logs, scheduled tasks) |
| P2 | **Full JSON rewrite on every write** | Delete 1 modpack = rewrite entire file | In-memory cache makes this less painful. SQLite long-term |
| P3 | **Docker container listing** | `ContainerService.resolveContainerReference():331-347` lists ALL containers per call | Cache container list with TTL (5-10s), invalidate on deploy/undeploy |
| P4 | **Sync task is O(n*m)** | n deployed packs * m total containers, every 15 seconds | Use cached container list. Or use structured concurrency (`StructuredTaskScope` with virtual threads) to inspect all packs concurrently |
| P5 | **Aggressive health polling** | `Home.tsx:58` — 2-second interval | Reduce to 5-10s. 2s is excessive for health status |
| P6 | **No route code splitting** | `App.tsx` loads all pages upfront | `React.lazy()` + `Suspense` for Home page (heaviest) |
| P7 | **No request deduplication** | Rapid clicks fire duplicate API calls | Debounce or disable buttons during pending actions (partially done via `activePackActions` but not for all ops) |
| P8 | **Race condition in JSON store** | Concurrent read-modify-write can lose updates | Wrap full read+modify+write under single write lock. Virtual threads make this less blocking |

---

## 4. INFRASTRUCTURE & DISTRIBUTION

### Build & CI

| # | Area | Current | Improvement |
|---|------|---------|-------------|
| I1 | **Tests always skipped** | `-DskipTests` in dev scripts + release.yml | Enable tests. 10 E2E test files exist but never run |
| I2 | **No PR CI workflow** | Only `release.yml` exists | Add `ci.yml`: trigger on PR + push to main. `mvn clean verify` (includes frontend build). Pin actions to commit SHAs. Declare minimal `permissions` |
| I3 | **No linting** | No ESLint, Prettier, Checkstyle | Add ESLint + Prettier for frontend, Checkstyle or PMD for backend |
| I4 | **No dependency scanning** | No Dependabot or Renovate | Add `dependabot.yml` for both Maven + npm ecosystems. Set `open-pull-requests-limit: 5` |
| I5 | **No security scanning in CI** | No SAST | Add CodeQL for Java + Trivy for filesystem scanning. Upload SARIF to GitHub Security tab |

### Docker & Deployment

| # | Area | Current | Improvement |
|---|------|---------|-------------|
| I6 | **No docker-compose** | Users run JAR directly | Create `docker-compose.yml` + `example.env` as release assets. Gold standard install: `wget compose + env → docker compose up -d` (pattern used by Immich, Gitea) |
| I7 | **No Docker image publishing** | Only JAR in GitHub Releases | Add GHCR publish job to release workflow. Multi-arch (`linux/amd64` + `linux/arm64`). Tag strategy: `latest`, `X.Y`, `X.Y.Z`. Use GHA layer cache |
| I8 | **Dockerfiles run as root** | No `USER` directive | Add non-root user per CLAUDE.md Docker best practices |
| I9 | **No HEALTHCHECK** | Neither Dockerfile has it | Add Spring Boot Actuator health endpoint + `HEALTHCHECK` in Dockerfile. Custom indicators: Docker daemon connectivity, disk space, data dir writability |
| I10 | **No .dockerignore** | Build context includes everything | Add `.dockerignore` for both backend and frontend |
| I11 | **Missing standard files** | No `.editorconfig`, `.env.example` | Add `.editorconfig` for cross-IDE consistency, `.env.example` for self-hosted setup |

### Self-Hosted Distribution (Best Practices from Immich/Gitea/Portainer)

**Target install experience (3 commands):**
```bash
mkdir mcmsm && cd mcmsm
wget -O docker-compose.yml https://github.com/kriskruse/McMSM/releases/latest/download/docker-compose.yml
wget -O .env https://github.com/kriskruse/McMSM/releases/latest/download/example.env
docker compose up -d
```

- All config via `.env` (ports, paths, memory) — users never edit compose file
- Named volumes for `data/` and `modpacks/` — upgrades preserve state
- Update path: `docker compose pull && docker compose up -d`
- Compatible with Tugtainer (Watchtower successor) for auto-updates

---

## 5. UI/UX IMPROVEMENTS

| # | Area | Current State | Improvement |
|---|------|---------------|-------------|
| U1 | **Empty state** | No message when 0 modpacks | Add illustration + "Upload your first modpack" CTA |
| U2 | **No dark/light toggle** | Hardcoded dark theme | Add `prefers-color-scheme` detection + manual toggle. Tailwind v4 supports this natively |
| U3 | **No loading skeletons** | "Loading modpacks..." text | Skeleton cards while loading — less layout shift |
| U4 | **No toast notifications** | Actions succeed silently or inline text | Add toast system for deploy/start/stop/delete confirmations |
| U5 | **Modal accessibility** | Missing `role="dialog"`, ARIA live regions | Add proper ARIA attributes throughout |
| U6 | **No keyboard shortcuts** | Mouse-only interaction | Add shortcuts for common actions (R = refresh, U = upload, / = search) |
| U7 | **Button style inconsistency** | Different hover states across components | Define button variant system (primary, danger, ghost, disabled) |
| U8 | **Error display on register** | Raw response text shown | Parse and display friendly error messages |
| U9 | **No favicon/branding** | Placeholder Tailwind logo on login/register | Custom McMSM logo or icon |

---

## 6. FEATURE IDEAS

### High Value

| # | Feature | Description |
|---|---------|-------------|
| F1 | **Server resource monitoring** | Live CPU, memory, disk usage per container via Docker stats API. Dashboard cards or mini-graphs |
| F2 | **Backup & restore** | One-click world backup (tar the server dir), scheduled auto-backups, restore from backup list. Critical for modded servers |
| F3 | **Scheduled tasks** | Auto-restart on crash detection, scheduled restarts (daily 4am), scheduled backups. Cron-like UI |
| F4 | **Modpack version management** | Track uploaded versions, rollback to previous, keep version history |
| F5 | **Import from CurseForge/Modrinth** | Paste modpack URL → auto-download ZIP, extract, configure. Biggest UX win for modpack management |
| F6 | **"Update available" notification** | `GET /api/version` endpoint + frontend check against GitHub releases API. Low effort, high value for self-hosted users |

### Medium Value

| # | Feature | Description |
|---|---------|-------------|
| F7 | **Player management** | Whitelist/ban management via server.properties + commands, online player count badge |
| F8 | **Notifications/webhooks** | Discord webhook on server crash, low disk space, update available. Simple POST to configured URL |
| F9 | **Bulk operations** | Start/stop/restart all servers, bulk archive. Useful when managing 5+ servers |
| F10 | **Search & filter** | Search by name, filter by status (running/stopped/archived), sort options. Essential as server count grows |
| F11 | **Custom JVM flags UI** | Visual editor for JVM arguments with presets (Aikar's flags, GraalVM, low-memory). Dropdown + custom input |
| F12 | **RCON support** | Native RCON protocol for commands instead of Docker exec + stdin pipe. More reliable, standard Minecraft approach |
| F13 | **Auto-update checker for modpacks** | Check CurseForge/Modrinth API if newer version of installed pack exists. Show badge on card |

### Nice to Have

| # | Feature | Description |
|---|---------|-------------|
| F14 | **Server network/proxy** | Manage Velocity/BungeeCord proxy linking multiple servers |
| F15 | **Activity log** | Timeline of actions: started, stopped, deployed, backed up. Useful for troubleshooting |
| F16 | **Map viewer integration** | Link to Dynmap/BlueMap if running. Or embed iframe |
| F17 | **Plugin/mod management** | Browse installed mods, toggle on/off, add/remove individual mods from pack |
| F18 | **Docker compose export** | Export a server's config as standalone `docker-compose.yml` for migration away from McMSM |
| F19 | **Multi-node support** | Manage Minecraft servers across multiple Docker hosts via TCP Docker API |
| F20 | **Drag-and-drop server ordering** | Custom sort order on dashboard |
| F21 | **Server groups/tags** | Organize servers into categories (modded, vanilla, testing) with color-coded labels |
| F22 | **Mobile-friendly UI** | Responsive improvements — manage servers from phone while away |
| F23 | **Server templates** | Pre-configured templates for popular modpacks (ATM, FTB, Vanilla) with auto-detected optimal settings |

### CI/CD & Workflow Automation

| # | Feature | Description |
|---|---------|-------------|
| F24 | **PR validation workflow** | `ci.yml` — build + test + lint on every PR |
| F25 | **Docker image auto-publish** | GHCR publish on release. Multi-arch. GHA layer cache. Artifact attestation for supply chain verification |
| F26 | **Dependabot config** | Auto-PR for Maven + npm dependency updates |
| F27 | **Pre-commit hooks** | Husky + lint-staged for frontend formatting/linting before commit |
| F28 | **Changelog generation** | Auto-generate from conventional commits on release |

---

## Summary Priorities

### Phase 1 — Stabilize & Quick Wins
- T1: Spring Boot → 4.0.1 GA
- T2: Enable virtual threads (1 line)
- T6: Update docker-java, fix response timeout
- I1: Enable tests in CI
- P5: Reduce health polling to 5-10s

### Phase 2 — Distribution & DX
- I6: Create docker-compose.yml + example.env
- I7: GHCR Docker image publishing
- I9: Add HEALTHCHECK + Actuator
- I2: PR CI workflow
- F6: Version check "update available" UI

### Phase 3 — Code Quality
- C1-C2: Split god objects
- C7: Split ModPack entity into Config + State
- C9-C12: Frontend cleanup (shared classes, Modal wrapper, auth context)
- T3-T5: Vite 8, React Router v7, React Compiler

### Phase 4 — Features (user priority)
- F2: Backup & restore (most requested for modded servers)
- F1: Resource monitoring
- F5: CurseForge/Modrinth import
- F3: Scheduled tasks
- F8: Discord webhooks
- Rest by user preference
