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

## How To Deploy And Run

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


## Configuration Notes
- Backend defaults are in `backend/src/main/resources/application.properties`
- Environment overrides are supported:
  - `MODPACKS_ROOT`
  - `TEMP_DIR`
  - `DATA_ROOT`
  - `RUNTIME_SYNC_INTERVAL_MS`
  - `MAX_UPLOAD_FILE_SIZE`
  - `MAX_UPLOAD_REQUEST_SIZE`

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
- Center align the X button on the modpack expanded view
- Add an indicator to the upload view between the upload done and form creation steps.
- Fix expanded modpack view auto-scroll when streaming logs to the console.
- Add a warning for port overlap when creating/editing modpacks.
- fix metadata edit form not listing minecraft versions.
- Add some overflow handling for the modpack name in the modpack card view.

#### Medium
- Add an indicator for the backend Docker connection status.
- Add a flow that tries to extract the Modpack version from the uploaded zip name and pre-fills the version field in the metadata form.
- Make the Dashboard do an upload when you drop a zip file on it, instead of just the upload view.

#### Big
- Make some end-to-end tests for the backend API using something like RestAssured to ensure the modpack lifecycle flows work as expected.
- Create a flow for releases on github with a build pipeline that builds the backend and frontend, creates a release, and uploads the built artifacts to the release.


### Security Considerations:
- Add hashing and encryption to user data.
- Do proper authentication and authorization for the API endpoints, just basic user auth.
- 

### Feature Ideas:
- Backup worlds
- Update modpacks by uploading a new zip and keeping the same metadata, world and optional configuration files.
- Proper flow to ensure modpack servers are ran with proper java args
- Create an update functionality for the manager application itself.
- Automated modpack downloading from CurseForge or Modrinth APIs.
- Modpack explorer to view possible modpacks to download and manage from the app itself.
