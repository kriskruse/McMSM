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
Triggered by pushing a version tag or `workflow_dispatch`. Builds the JAR and creates a GitHub release as `McMSM-{version}.jar`.

```bash
git tag v1.0 && git push origin v1.0
```

The CI workflow (`.github/workflows/release.yml`) uses Java 25 Temurin and Node.js 22. Version is read from `backend/pom.xml`. Release notes are auto-generated from commits since the previous tag, formatted as:
```
- `{shortSha}` - {title} - {author}
```

## Java Coding Standards

Apply to all `.java` files:

- **DTOs and data holders** → use Java Records instead of classes
- **Pattern matching** → use `instanceof` patterns and `switch` expressions
- **Type inference** → use `var` when the type is obvious from the right-hand side
- **Immutability** → prefer `final`, `List.of()`, `Map.of()`, `Stream.toList()`
- **Streams** → use Streams API + lambdas/method references for collections
- **Null handling** → avoid `null`; use `Optional<T>` for possibly-absent values
- **Resources** → always use try-with-resources for streams, files, sockets (S2095)
- **Empty catch blocks** → always log or handle exceptions (S1188)
- **Magic numbers** → replace with named constants (S109)
- **Long methods** → break into smaller units; reduce cognitive complexity (S138, S3776)

**Naming** (Google Java style): `UpperCamelCase` classes, `lowerCamelCase` methods/variables, `UPPER_SNAKE_CASE` constants, `lowercase` packages. Nouns for classes, verbs for methods. No abbreviations.

### Javadoc
Public and protected members must have Javadoc. Use `@param`, `@return`, `@throws`. First sentence is the summary and ends with a period. Use `{@inheritDoc}` unless behavior differs meaningfully.

### Maven
Do **not** use `org.springframework.boot:spring-boot-starter-web` — use `spring-boot-starter-webmvc` instead.

## Docker Best Practices

Apply when writing Dockerfiles or docker-compose files:

- Use **multi-stage builds** for compiled languages to keep runtime images small
- Use **minimal base images** (`alpine`, `slim`, or `distroless`); pin specific version tags, never `latest` in production
- Run as a **non-root user** — create a dedicated user/group and `chown` app files
- Combine `RUN` commands and clean up in the same layer (`rm -rf /var/lib/apt/lists/*`)
- Copy dependency files before source code to maximize layer cache reuse
- Use `EXPOSE` to document ports; use `HEALTHCHECK` for orchestration readiness
- Never hardcode secrets in image layers; use environment variables or runtime secrets
- Use named volumes for persistent data; never store state in the container writable layer

## Contribution Notes
When modifying existing code, explain the rationale — why the change is better or what problem it solves, especially for refactors.