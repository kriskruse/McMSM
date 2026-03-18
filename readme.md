# McMSM

## Description
McMSM is a Minecraft modpack server manager.
It lets you upload modpacks, store metadata, deploy a Docker container for each pack, and control runtime actions from a React UI.

### Core Features
- Upload and register modpacks in the backend database
- Edit modpack metadata (name, versions, Java config, port, entrypoint)
- Deploy, start, stop, delete, and archive modpack runtime containers
- Track saved vs deployed instances with live runtime state sync

## Stack

### Frontend
- React 19 + TypeScript
- Vite
- Tailwind CSS

### Backend
- Java 25 (Temurin)
- Spring Boot (Web + JPA)
- Maven
- Docker Java client for container lifecycle operations

### Data + Infrastructure
- PostgreSQL 18 (Docker Compose)
- Docker / Docker Compose

## How To Deploy And Run

### Prerequisites
- Docker Desktop (or Docker Engine + Compose)
- Java 25 JDK
- Maven 3.9+
- Node.js 20+ and npm (for frontend local run)

### 1) Clone repository
```powershell
git clone <your-repo-url>
cd "McMSM"
```

### 2) Start database
```powershell
docker compose up -d postgres
```

### 3) Run backend
Option A (helper script):
```powershell
.\buildAndRunBackend.ps1
```

Option B (manual):
```powershell
cd backend
mvn spring-boot:run
```

The backend runs on `http://localhost:8080` and exposes API under `/api`.

### 4) Run frontend (new terminal)
```powershell
cd frontend
npm install
npm run dev
```

Vite serves the frontend locally (default `http://localhost:5173`).

## Configuration Notes
- Backend DB defaults are in `backend/src/main/resources/application.properties`
- Environment overrides are supported:
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `MODPACKS_ROOT`
  - `TEMP_DIR`
  - `RUNTIME_SYNC_INTERVAL_MS`

## Modpack Lifecycle Notes
- **Saved instance**: files + metadata exist, but no active deployment container
- **Deployed instance**: container exists and can be started/stopped
- **Archive**: removes deployed container and marks pack as saved while retaining modpack files
- Backend now removes stale DB entries if modpack folders are missing on refresh/update flows

## Useful Development Commands
```powershell
# Backend tests
cd backend
mvn test

# Frontend production build
cd frontend
npm run build
```

## TODO:
- Center align the X button on the modpack expanded view
- Add an indicator to the upload view between the upload done and form creation steps.
- Make some end-to-end tests for the backend API using something like RestAssured to ensure the modpack lifecycle flows work as expected.
- Create a flow for releases on github with a build pipeline that builds the backend and frontend, creates a release, and uploads the built artifacts to the release.


## Feature Ideas:
- Backup worlds
- Update modpacks by uploading a new zip and keeping the same metadata, world and optional configuration files.
- Proper flow to ensure modpack servers are ran with proper java args
- Create an update functionality for the manager application itself.
