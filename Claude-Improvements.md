# McMSM Full Project Audit: Improvements & Feature Ideas

## Context

Full codebase audit of McMSM — a self-hosted Minecraft modpack server manager. User=host, so security is out of scope. Covers code quality, performance, tech stack upgrades, UI/UX, infrastructure, distribution, and feature opportunities. Refined with web research on current best practices (2025-2026).

**Status legend** (audit re-run 2026-05-25 against current source):
- ✅ **DONE** — implemented in repo
- 🟡 **PARTIAL** — partly implemented, gaps noted
- ❌ **NOT DONE** — no evidence in code

---

## 2. INFRASTRUCTURE & DISTRIBUTION

### Build & CI

| # | Area | Current | Improvement |
|---|------|---------|-------------|
| ✅ I1 | **Tests always skipped** | `-DskipTests` in dev scripts + release.yml | Enable tests. 10 E2E test files exist but never run. Done: release.yml now runs `mvn verify`; dev scripts still skip per CLAUDE.md (fast iteration) |
| ✅ I2 | **No PR CI workflow** | Only `release.yml` exists | Add `ci.yml`: trigger on PR + push to main. `mvn clean verify` (includes frontend build). Pin actions to commit SHAs. Declare minimal `permissions`. Done: `.github/workflows/ci.yml` |
| ✅ I3 | **No linting** | No ESLint, Prettier, Checkstyle | Add ESLint + Prettier for frontend, Checkstyle or PMD for backend. Done: frontend `eslint.config.js` + `.prettierrc.json` + scripts; backend `maven-checkstyle-plugin` (Google style, warn-only) |
| ✅ I4 | **No dependency scanning** | No Dependabot or Renovate | Add `dependabot.yml` for both Maven + npm ecosystems. Set `open-pull-requests-limit: 5`. Done: `.github/dependabot.yml` covers npm + maven + github-actions ecosystems |
| ✅ I5 | **No security scanning in CI** | No SAST | Add CodeQL for Java + Trivy for filesystem scanning. Upload SARIF to GitHub Security tab. Done: `.github/workflows/codeql.yml` (java-kotlin + javascript-typescript matrix); Trivy SARIF step added to `ci.yml` |

---

## 4. FEATURE IDEAS

### High Value

| # | Feature | Description                                                                                                                |
|---|---------|----------------------------------------------------------------------------------------------------------------------------|
| ✅ F1 | **Server resource monitoring** | Live CPU, memory, disk usage per container via Docker stats API. Dashboard cards or mini-graphs. Done: `ContainerStatsService`, `ContainerMetricsPanel.tsx`, `GET /{packId}/stats` |
|✅ F1.1| **Server system resource monitoring** | Host-level CPU, memory, disk space monitoring. Done: `SystemStatsController`, `SystemStatsService`, oshi-core dep |
| ❌ F2 | **Backup & restore** | One-click world backup (tar the server dir), scheduled auto-backups, restore from backup list. Critical for modded servers |
| 🟡 F3 | **Scheduled tasks** | Auto-restart on crash detection, scheduled restarts (daily 4am), scheduled backups. Cron-like UI. Partial: only fixed-interval Docker health poll (`ContainerService.java:55`), no user-configurable cron |
| 🟡 F4 | **Modpack version management** | Track uploaded versions, rollback to previous, keep version history. Partial: `packVersion` field + update endpoint exist; no history/rollback |
| ❌ F5 | **Import from CurseForge/Modrinth** | In-app browse + search + install. Paste URL OR pick from catalog. Auto-download ZIP via official APIs, extract, configure. Biggest UX win for modpack management. **See F5-design below.** (LoaderDetector mentions manifest-only CF/MR detection but no source services exist) |
| ✅ F6 | **"Update available" notification** | `GET /api/version` endpoint + frontend check against GitHub releases API. Low effort, high value for self-hosted users. Done: `UpdateController` (`/version`, `/check`), `UpdateButton.tsx` |

### Medium Value

| # | Feature | Description |
|---|---------|-------------|
| ❌ F7 | **Player management** | Whitelist/ban management via server.properties + commands, online player count badge. (Files preserved on update but no CRUD endpoints) |
| ❌ F8 | **Notifications/webhooks** | Discord webhook on server crash, low disk space, update available. Simple POST to configured URL |
| ❌ F9 | **Bulk operations** | Start/stop/restart all servers, bulk archive. Useful when managing 5+ servers |
| 🟡 F10 | **Search & filter** | Search by name, filter by status (running/stopped/archived), sort options. Essential as server count grows. Partial: client-side deployed filter in `useModpacks.ts`; no search or server-side filter |
| ❌ F11 | **Custom JVM flags UI** | Visual editor for JVM arguments with presets (Aikar's flags, GraalVM, low-memory). Dropdown + custom input. (Only `javaXmx` in metadata modal) |
| ❌ F12 | **RCON support** | Native RCON protocol for commands instead of Docker exec + stdin pipe. More reliable, standard Minecraft approach |
| ❌ F13 | **Auto-update checker for modpacks** | Check CurseForge/Modrinth API if newer version of installed pack exists. Show badge on card. Reuse F5 service layer + persisted `sourceId` per ModPack |

### Nice to Have

| # | Feature | Description |
|---|---------|-------------|
| ❌ F14 | **Server network/proxy** | Manage Velocity/BungeeCord proxy linking multiple servers |
| ❌ F15 | **Activity log** | Timeline of actions: started, stopped, deployed, backed up. Useful for troubleshooting |
| ❌ F16 | **Map viewer integration** | Link to Dynmap/BlueMap if running. Or embed iframe |
| ❌ F17 | **Plugin/mod management** | Browse installed mods, toggle on/off, add/remove individual mods from pack |
| ❌ F18 | **Docker compose export** | Export a server's config as standalone `docker-compose.yml` for migration away from McMSM |
| ❌ F19 | **Multi-node support** | Manage Minecraft servers across multiple Docker hosts via TCP Docker API |
| ❌ F20 | **Drag-and-drop server ordering** | Custom sort order on dashboard |
| ❌ F21 | **Server groups/tags** | Organize servers into categories (modded, vanilla, testing) with color-coded labels |
| ❌ F22 | **Mobile-friendly UI** | Responsive improvements — manage servers from phone while away |
| ❌ F23 | **Server templates** | Pre-configured templates for popular modpacks (ATM, FTB, Vanilla) with auto-detected optimal settings |

### CI/CD & Workflow Automation

| # | Feature | Description |
|---|---------|-------------|
| ❌ F24 | **PR validation workflow** | `ci.yml` — build + test + lint on every PR |
| ❌ F25 | **Docker image auto-publish** | GHCR publish on release. Multi-arch. GHA layer cache. Artifact attestation for supply chain verification |
| ❌ F26 | **Dependabot config** | Auto-PR for Maven + npm dependency updates |
| ❌ F27 | **Pre-commit hooks** | Husky + lint-staged for frontend formatting/linting before commit |
| 🟡 F28 | **Changelog generation** | Auto-generate from conventional commits on release. Partial: release.yml builds notes from git log; no conventional-commit enforcement |

---

## F5-design — CurseForge / Modrinth Modpack Browser

### Goal
In-app catalog browse + search + one-click install for Minecraft modpacks. Replace the current "go to website, download ZIP, upload via UI" loop.

### Provider Strategy — Modrinth first, CurseForge second

| Provider | Auth | ToS-friendly | Catalog size | Verdict |
|----------|------|--------------|--------------|---------|
| **Modrinth** | None (`https://api.modrinth.com/v2`) | Permissive, caching allowed, no key sharing issue | Smaller, growing fast | **Ship first** |
| **CurseForge** | API key, manual application (48-72h review) | Strict — no caching, key non-transferable, must honor `allowModDistribution` | Largest modpack catalog | **Ship second, gated behind user-supplied key** |

Build provider-agnostic interface (`ModpackSourceService`) so UI is one search bar with a provider toggle.

### CurseForge ToS Compliance Rules (non-negotiable)

1. **No bundled API key.** Each end-user applies for and supplies their own key via Settings UI → persisted in `data/settings.json` or env var. Release binaries ship key-less.
2. **No response caching.** Every search and metadata call is a live request. Mitigate UX cost via debounced search input + pagination, not server caching.
3. **Honor `allowModDistribution`.** If file flag is `false`, never download — show "Open on CurseForge" button that links to the project page in the user's browser.
4. **Attribution.** Every modpack card shows source ("via CurseForge" / "via Modrinth"), author name, link to canonical project page.
5. **No mirroring / re-hosting.** Downloads stream from provider CDN URL straight to the user's instance, never proxied through any centralized McMSM server.

### Backend Sketch

| Component | Purpose |
|-----------|---------|
| `ModpackSourceService` (interface) | `search(query, page)`, `getVersions(projectId)`, `download(fileId)` |
| `ModrinthSourceService` | Implements interface against `api.modrinth.com/v2` |
| `CurseForgeSourceService` | Implements interface against `api.curseforge.com/v1`. Reads API key from `application.properties` / env var. No-ops gracefully if key absent |
| `ModpackBrowserController` | `GET /api/sources` (list enabled), `GET /api/sources/{src}/search`, `GET /api/sources/{src}/{id}/files`, `POST /api/sources/{src}/{id}/install` |
| Reuse `McModPackService` import flow | Downloaded ZIP lands in `TEMP_DIR`, existing extract + metadata pipeline runs |

Persist `sourceProvider` + `sourceProjectId` + `sourceFileId` on `ModPack` entity → enables F13 (update check) for free.

### Frontend Sketch

- New "Browse" button on dashboard → opens `ModpackBrowserModal`.
- Modal: provider toggle (Modrinth | CurseForge), search input (debounced 300ms), result grid (image, name, author, downloads, MC versions).
- Card → version picker → "Install" → progress toast → modpack appears on dashboard.
- If CurseForge selected and no key configured → inline prompt linking to Settings + the CurseForge application form.

### CurseForge API Application
Submitted 2026-05-25. Form covers project scope, business model (none), distribution policy (respect `allowModDistribution`), and the self-hosted key-distribution model. Expect 48-72h reviewer response.

### Open Questions
- Where to surface `allowModDistribution=false` packs? Hide entirely, or show greyed-out with "Open in browser" link? Lean towards the latter — discovery > hiding.
- Cache the catalog at all? CurseForge ToS says no. Modrinth permits it. Probably not worth two code paths — live fetch for both, keep code uniform.
- Rate-limit handling? Both APIs return 429. Surface as toast: "rate-limited, retry in Xs".

---

## Audit Snapshot (2026-05-25)

### T-Series (Tech Stack)
- ❌ **T1** Spring Boot — pom on `4.1.0-M2` (milestone, not 4.0.1 GA)
- ✅ **T2** Virtual threads — `application.properties:12` sets `spring.threads.virtual.enabled=true`
- ✅ **T3** Vite 8 — `frontend/package.json` `^8.0.8`
- ✅ **T4** React Router v7 — `^7.14.1`
- ✅ **T5** React Compiler — `babel-plugin-react-compiler ^1.0.0`, wired in `vite.config.ts`
- 🟡 **T6** docker-java — at `3.7.1` with `httpclient5` transport; no explicit timeout config

### C-Series (Code Quality)
- ❌ **C1-C2** God objects — `McModPackService` ~509 lines, unsplit
- ❌ **C7** Split `ModPack` entity into Config + State — single class still mixes `packVersion`, `status`, `containerId`, `lastDeployError`
- ❌ **C9-C12** Frontend cleanup — no shared modal wrapper, no auth context refactor seen

### Totals
- ✅ DONE: F1, F1.1, F6, T2, T3, T4, T5 (7 items)
- 🟡 PARTIAL: F3, F4, F10, F28, T6 (5 items)
- ❌ NOT DONE: I1-I5, F2, F5, F7-F9, F11-F27 minus the done ones, T1, C1-C12 (rest)

---

## Summary Priorities

### Phase 1 — Stabilize & Quick Wins
- ❌ T1: Spring Boot → 4.0.1 GA
- ✅ T2: Enable virtual threads (1 line) — **done**
- 🟡 T6: Update docker-java, fix response timeout
- ✅ I1: Enable tests in CI — **done** (release.yml `mvn verify`)
- ❓ P5: Reduce health polling to 5-10s (currently 15s default)

### Phase 2 — Distribution & DX
- ❓ I6: Create docker-compose.yml + example.env
- ❌ I7 / F25: GHCR Docker image publishing
- ❓ I9: Add HEALTHCHECK + Actuator
- ✅ I2: PR CI workflow — **done** (`.github/workflows/ci.yml`)
- ✅ F6: Version check "update available" UI — **done**

### Phase 3 — Code Quality
- ❌ C1-C2: Split god objects
- ❌ C7: Split ModPack entity into Config + State
- ❌ C9-C12: Frontend cleanup (shared classes, Modal wrapper, auth context)
- ✅ T3-T5: Vite 8, React Router v7, React Compiler — **done**

### Phase 4 — Features (user priority)
- ❌ F2: Backup & restore (most requested for modded servers)
- ✅ F1: Resource monitoring — **done**
- ❌ F5: Modpack browser — Modrinth first (no key), CurseForge once API key approved (see F5-design)
- 🟡 F3: Scheduled tasks (only health-poll exists)
- ❌ F8: Discord webhooks
- Rest by user preference
