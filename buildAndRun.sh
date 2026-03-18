#!/usr/bin/env bash

set -euo pipefail

PROJECT_NAME="mcmsm"
COMPOSE_FILE="docker-compose.yml"
BACKEND_POM="backend/pom.xml"
FRONTEND_DIR="frontend"
SKIP_FRONTEND_INSTALL=false

usage() {
    cat <<'USAGE'
Usage: ./buildAndRun.sh [options]

Options:
  -ProjectName, --project-name <name>          Docker Compose project name (default: mcmsm)
  -ComposeFile, --compose-file <path>          Compose file path relative to repo root (default: docker-compose.yml)
  -BackendPom, --backend-pom <path>            Backend pom.xml path relative to repo root (default: backend/pom.xml)
  -FrontendDir, --frontend-dir <path>          Frontend directory relative to repo root (default: frontend)
  -SkipFrontendInstall, --skip-frontend-install Skip npm install before frontend build
  -h, --help                                   Show this help message
USAGE
}

require_value() {
    local flag="$1"
    local value="$2"
    if [[ -z "$value" || "$value" == -* ]]; then
        echo "Missing value for $flag" >&2
        usage
        exit 1
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -ProjectName|--project-name)
            require_value "$1" "${2:-}"
            PROJECT_NAME="$2"
            shift 2
            ;;
        -ComposeFile|--compose-file)
            require_value "$1" "${2:-}"
            COMPOSE_FILE="$2"
            shift 2
            ;;
        -BackendPom|--backend-pom)
            require_value "$1" "${2:-}"
            BACKEND_POM="$2"
            shift 2
            ;;
        -FrontendDir|--frontend-dir)
            require_value "$1" "${2:-}"
            FRONTEND_DIR="$2"
            shift 2
            ;;
        -SkipFrontendInstall|--skip-frontend-install)
            SKIP_FRONTEND_INSTALL=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
done

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_PATH="$SCRIPT_ROOT/$COMPOSE_FILE"
BACKEND_POM_PATH="$SCRIPT_ROOT/$BACKEND_POM"
BACKEND_DIR_PATH="$(dirname "$BACKEND_POM_PATH")"
FRONTEND_PATH="$SCRIPT_ROOT/$FRONTEND_DIR"
FRONTEND_PACKAGE_JSON_PATH="$FRONTEND_PATH/package.json"

if [[ ! -f "$COMPOSE_PATH" ]]; then
    echo "Compose file not found: $COMPOSE_PATH" >&2
    exit 1
fi

if [[ ! -f "$BACKEND_POM_PATH" ]]; then
    echo "Backend pom.xml not found: $BACKEND_POM_PATH" >&2
    exit 1
fi

if [[ ! -f "$FRONTEND_PACKAGE_JSON_PATH" ]]; then
    echo "Frontend package.json not found: $FRONTEND_PACKAGE_JSON_PATH" >&2
    exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is not available on PATH." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose is not available on PATH." >&2
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is not available on PATH." >&2
    exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
    echo "npm is not available on PATH." >&2
    exit 1
fi

compose_cmd() {
    docker compose -p "$PROJECT_NAME" -f "$COMPOSE_PATH" "$@"
}

echo "[1/6] Checking Postgres container state in project '$PROJECT_NAME'..."
POSTGRES_CONTAINER_ID="$(compose_cmd ps -q postgres | tr -d '\r')"

if [[ -z "$POSTGRES_CONTAINER_ID" ]]; then
    echo "[2/6] Postgres is not running. Starting Postgres..."
    compose_cmd up -d postgres
    POSTGRES_CONTAINER_ID="$(compose_cmd ps -q postgres | tr -d '\r')"
else
    POSTGRES_RUNNING="$(docker inspect -f '{{.State.Running}}' "$POSTGRES_CONTAINER_ID" | tr -d '\r')"
    if [[ "$POSTGRES_RUNNING" != "true" ]]; then
        echo "[2/6] Postgres container exists but is stopped. Starting Postgres..."
        compose_cmd up -d postgres
        POSTGRES_CONTAINER_ID="$(compose_cmd ps -q postgres | tr -d '\r')"
    else
        echo "[2/6] Postgres is already running."
    fi
fi

if [[ -z "$POSTGRES_CONTAINER_ID" ]]; then
    echo "Could not resolve Postgres container ID after startup." >&2
    exit 1
fi

echo "[3/6] Waiting for Postgres health check to report healthy..."
MAX_ATTEMPTS=24
attempt=1
while [[ $attempt -le $MAX_ATTEMPTS ]]; do
    HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "$POSTGRES_CONTAINER_ID" | tr -d '\r')"
    if [[ "$HEALTH" == "healthy" ]]; then
        break
    fi

    if [[ $attempt -eq $MAX_ATTEMPTS ]]; then
        echo "Postgres did not become healthy in time. Last status: $HEALTH" >&2
        exit 1
    fi

    sleep 2
    attempt=$((attempt + 1))
done

echo "[4/6] Building backend..."
(
    cd "$BACKEND_DIR_PATH"
    mvn clean package -DskipTests
)

echo "[5/6] Building frontend..."
(
    cd "$FRONTEND_PATH"
    if [[ "$SKIP_FRONTEND_INSTALL" != "true" ]]; then
        npm install
    fi
    npm run build
)

echo "[6/6] Starting backend and frontend..."
BACKEND_LOG="$BACKEND_DIR_PATH/backend-dev.log"
FRONTEND_LOG="$FRONTEND_PATH/frontend-dev.log"

BACKEND_PID="$(
    cd "$BACKEND_DIR_PATH"
    nohup mvn spring-boot:run >"$BACKEND_LOG" 2>&1 &
    echo $!
)"

FRONTEND_PID="$(
    cd "$FRONTEND_PATH"
    nohup npm run dev >"$FRONTEND_LOG" 2>&1 &
    echo $!
)"

echo "Backend started in background (PID: $BACKEND_PID). Logs: $BACKEND_LOG"
echo "Frontend started in background (PID: $FRONTEND_PID). Logs: $FRONTEND_LOG"
echo "Postgres is running via Docker Compose project '$PROJECT_NAME'."

