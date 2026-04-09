# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

McMSM (Minecraft Modpack Server Manager) is a self-hosted full-stack application for managing Minecraft modpack servers. Users upload modpack ZIPs, configure metadata, and manage Docker container lifecycles (deploy, start, stop, delete, archive) via a web UI.

## Commands

### Build & Run (recommended)
```bash
# Windows
.\devBuildAndRun.ps1

# Linux/macOS
./devBuildAndRun.sh
```

Both scripts run `mvn clean package -DskipTests` (which auto-builds the frontend), then `mvn spring-boot:run`.

### Manual workflow
```bash
# Build backend + frontend bundle
cd backend && mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

### Frontend (dev server only)
```bash
cd frontend
npm ci
npm run dev    # Vite dev server on http://localhost:5173 (proxies /api → :8080)
npm run build  # TypeScript compile + bundle → frontend/dist/
```

App URL: `http://localhost:8080` — API under `/api`.

### Prerequisites
Docker Desktop (or Engine), Java 25 JDK, Maven 3.9+, Node.js 20+.

## Architecture

### Frontend — `frontend/src/`
React 19 + TypeScript + Vite + Tailwind CSS.

- **`pages/`** — Login, Register, Home (main dashboard)
- **`components/`** — ModpackCard, ModpackConsole (live Docker logs), ModpackMetadataModal, UploadModpackModal, BackendStatusIndicator
- **`util/modpackApi.ts`** — All HTTP calls to the backend API
- **`dto/`** — TypeScript type definitions mirroring backend DTOs

Vite proxies `/api/*` to `localhost:8080` during development. The production build is copied into `backend/target/classes/static/` by Maven, and Spring Boot's `SpaForwardController` handles SPA routing fallback.

### Backend — `backend/src/main/java/dk/broegger_kruse/mcmsm/`
Java 25 + Spring Boot 4.1.0-M2 + Maven. Layered architecture:

| Layer | Key Classes | Responsibility |
|---|---|---|
| Controllers | `ModPackController`, `LoginController`, `SpaForwardController` | REST endpoints + SPA fallback |
| Services | `McModPackService`, `FileService`, `ContainerService` | Business logic, file I/O, Docker integration |
| Repositories | `FileModPackRepository`, `FileUserRepository` | JSON-file persistence |
| Entities/DTOs | `ModPack`, `UserEntity` + request/response DTOs | Data model + API contracts |

**Data persistence** is entirely file-based (no database):
- `data/modpacks.json` — modpack metadata array
- `data/users.json` — user credentials
- `modpacks/{packId}/` — extracted modpack ZIP contents

**Docker integration** (`ContainerService`) uses the docker-java library. Containers are named `modpack-{packId}`, mount the modpack directory to `/server`, and are selected by Java version (e.g., Java 21 → `eclipse-temurin:21`). A scheduled task polls Docker every `RUNTIME_SYNC_INTERVAL_MS` (default 15s) to sync container state.

### Maven build pipeline
The `prepare-package` phase runs `npm ci` + `npm run build` in `frontend/`, then copies `frontend/dist/` into `backend/target/classes/static/`. This produces a single self-contained executable JAR.

## Environment Variables
Configured in `backend/src/main/resources/application.properties`, all overridable:

| Variable | Default | Purpose |
|---|---|---|
| `MODPACKS_ROOT` | `modpacks/` | Modpack file storage |
| `DATA_ROOT` | `data/` | JSON metadata storage |
| `TEMP_DIR` | system temp | Temp upload directory |
| `RUNTIME_SYNC_INTERVAL_MS` | `15000` | Docker state poll interval |
| `MAX_UPLOAD_FILE_SIZE` | `8024MB` | Max upload file size |
| `MAX_UPLOAD_REQUEST_SIZE` | `8024MB` | Max request size |

## Releases
Triggered by pushing a version tag or `workflow_dispatch` from `.github/workflows/release.yml`. Builds the JAR and creates a GitHub release as `McMSM-{version}.jar`.

```bash
git tag v1.0 && git push origin v1.0
```

## Contribution Notes
When modifying existing code, explain the rationale — why the change is better or what problem it solves, especially for refactors. Use Java 25 Temurin features and best practices on the backend.