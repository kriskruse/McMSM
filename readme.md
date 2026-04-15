# McMSM

![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)


McMSM (Minecraft Modpack Server Manager) is a full-stack application designed to simplify the management of Minecraft modpack servers. 
It patches a personal need for a lightweight, self-hosted solution to manage modpacks without relying on third-party services or complex server setups.
The application allows users to upload modpacks, configure server settings, and manage the lifecycle of modpack instances through an intuitive web interface. 

The project is meant to solve a untouch pain point for people who host multiple modpack servers for themselves and their friends.
The project is open to contributions and feedback, and I hope it can grow into a robust tool.



### Core Features
Currently, the following features are implemented, but hoping to expand in the future:
- Upload and register modpacks in the backend database
- Edit modpack metadata (name, versions, Java config, port, entrypoint)
- Deploy, start, stop, delete, and archive modpack runtime containers
- Track saved vs deployed instances with live runtime state sync

Currently supports Forge and NeoForge modpacks.


## Getting Started

### Prerequisites
- **Java 25** JDK (e.g., [Eclipse Temurin](https://adoptium.net/))
- **Docker Desktop** (or Docker Engine) — required for managing modpack containers

### Quick Start
1. Download the latest `McMSM-{version}.jar` from [Releases](../../releases).
2. Run the JAR:
   ```bash
   java -jar McMSM-2.0.jar
   ```
3. Open `http://localhost:8080` in your browser.

### Configuration
All settings can be overridden via environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `MODPACKS_ROOT` | `modpacks/` | Where modpack files are stored |
| `DATA_ROOT` | `data/` | Where metadata JSON files are stored |
| `TEMP_DIR` | system temp | Temporary upload directory |
| `RUNTIME_SYNC_INTERVAL_MS` | `15000` | Docker state polling interval (ms) |
| `MAX_UPLOAD_FILE_SIZE` | `15360MB` | Max upload file size |
| `MAX_UPLOAD_REQUEST_SIZE` | `15360MB` | Max request size |

Example with custom data directory:
```bash
DATA_ROOT=/path/to/data java -jar McMSM-2.0.jar
```

On Windows (PowerShell):
```powershell
$env:DATA_ROOT="C:\mcmsm\data"; java -jar McMSM-2.0.jar
```

# Technical details and contribution guide

## Stack

### Frontend
- React 19 + TypeScript
- Vite
- Tailwind CSS

### Backend
- Java 25 (Temurin)
- Spring Boot (Web + JPA)
- Maven

### Data + Infrastructure
- File-based metadata storage (`backend/data` by default)
- Docker (for managed Minecraft modpack runtime containers)

## Development Setup

### Prerequisites
- Docker Desktop (or Docker Engine)
- Java 25 JDK
- Maven 3.9+
- Node.js 20+ and npm

### 1) Clone repository
```powershell
git clone <repo-url>
cd "McMSM"
```

### 2) Build and run with the helper script

Windows (PowerShell):
```powershell
.\devBuildAndRun.ps1
```

Linux/macOS (bash):
```bash
chmod +x ./devBuildAndRun.sh
./devBuildAndRun.sh
```

What this script does:
- Builds backend with `mvn clean package -DskipTests`
- During backend packaging, runs frontend `npm ci` + `npm run build`
- Copies frontend build output into backend static resources
- Starts backend with `mvn spring-boot:run`

Application runs on `http://localhost:8080` and exposes API under `/api`.

### 3) Manual workflow (alternative)
```powershell
# Build package (includes frontend build + bundle copy)
cd backend
mvn clean package -DskipTests

# Run backend
mvn spring-boot:run
```


## Releases (GitHub Actions)
- Workflow file: `.github/workflows/release.yml`
- It builds backend from the newest commit (including bundled frontend), renames artifact to `McMSM-{version}.jar`, and creates a GitHub release.
- Release title format: `McMSM Release version {version}`
- Release notes start with `# McMSM Release version {version}` and include one line per commit since the previous release tag:
  - `{commit id link} - title - description - by {Author}`

### How to control release flow
- `workflow_dispatch` (manual): start from the Actions tab and optionally provide:
  - `tag` (defaults to `v{version}` from `backend/pom.xml`)
  - `draft` (`true`/`false`)
  - `prerelease` (`true`/`false`)
- `push.tags` (automatic): pushing a matching tag triggers release automatically.

Example tag workflow:
```powershell
git tag v1.0
git push origin v1.0
```

Manual trigger options:
1. Use manual dispatch when you want approval gates or custom tag/draft/prerelease flags.
2. Use tags when you want release creation to be part of your normal Git flow.
3. Use both: create tags for stable versions, manual dispatch for hotfix/prerelease control.



## TODO:
### Notes
- FTB uses install scripts to get modpack data instead of uploading everything as a zip. How do we manage that?


### Fixes and Bugs:
#### Small

#### Medium
- We have a lot of OS and Jar path checks in UpdateService should we move this to global variables as these don't change?

#### Big
- Make some end-to-end tests for the backend API using something like RestAssured to ensure the modpack lifecycle flows work as expected.


### Security Considerations:
- Add hashing and encryption to user data.
- Do proper authentication and authorization for the API endpoints, just basic user auth.

### Future Feature Ideas:
- Backup worlds
- Web page to edit the JVM args template used for new and updated modpacks.
- Automated modpack downloading from CurseForge, Modrinth and FTB APIs.
- Modpack explorer to view possible modpacks to download and manage from the app itself.

## License

McMSM is dual-licensed.

- Open-source license: `AGPL-3.0-only`.
- Commercial closed-source use: available under a separate proprietary license from the repository owner and major contributors, with terms to be discussed on a case-by-case basis.

You can use McMSM commercially under AGPL-3.0 as long as you follow AGPL obligations (including sharing corresponding source code for modified network services/distributions).

