# McMSM

![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![Postgres](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)


McMSM (Minecraft Modpack Server Manager) is a full-stack application designed to simplify the management of Minecraft modpack servers. 
It patches a personal need for a lightweight, self-hosted solution to manage modpacks without relying on third-party services or complex server setups.
The application allows users to upload modpacks, configure server settings, and manage the lifecycle of modpack instances (deploy, start, stop, delete) through an intuitive web interface. 

The project is meant to solve a untouch pain point for people who host multiple modpack servers for themselves and their friends.
The project is open to contributions and feedback, and I hope it can grow into a robust tool.

### Core Features
Currently, the following features are implemented, but hoping to expand in the future:
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
- Node.js 20+ and npm
- PowerShell 5.1+ (Windows) or PowerShell 7+ (for `buildAndRun.ps1`)
- Bash (for `buildAndRun.sh` on Linux/macOS)

### 1) Clone repository
```powershell
git clone <your-repo-url>
cd "McMSM"
```

### 2) Build and run everything with the helper script

Windows (PowerShell):
```powershell
.\buildAndRun.ps1
```

Linux/macOS (bash):
```bash
chmod +x ./buildAndRun.sh
./buildAndRun.sh
```

What this script does:
- Ensures Docker Compose, Maven, and npm are available
- Starts the `postgres` container if needed and waits for healthy status
- Builds backend with `mvn clean package -DskipTests`
- Installs frontend dependencies (unless `-SkipFrontendInstall` is used)
- Builds frontend with `npm run build`
- Starts backend (`mvn spring-boot:run`) and frontend (`npm run dev`)

Optional script parameters:
```powershell
.\buildAndRun.ps1 -SkipFrontendInstall
.\buildAndRun.ps1 -ProjectName mcmsm -ComposeFile docker-compose.yml -BackendPom backend/pom.xml -FrontendDir frontend
```

```bash
./buildAndRun.sh --skip-frontend-install
./buildAndRun.sh --project-name mcmsm --compose-file docker-compose.yml --backend-pom backend/pom.xml --frontend-dir frontend
```

Process behavior note:
- `buildAndRun.ps1` opens backend and frontend in new PowerShell windows.
- `buildAndRun.sh` starts backend and frontend in background and writes logs to `backend/backend-dev.log` and `frontend/frontend-dev.log`.

Backend runs on `http://localhost:8080` and exposes API under `/api`.
Frontend runs on Vite default `http://localhost:5173`.

### 3) Manual workflow (alternative)
```powershell
# Start database
docker compose up -d postgres

# Run backend
cd backend
mvn spring-boot:run

# Run frontend (new terminal)
cd ..\frontend
npm install
npm run dev
```


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
