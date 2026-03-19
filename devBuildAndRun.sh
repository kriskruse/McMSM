#!/usr/bin/env bash

set -euo pipefail

BACKEND_POM="backend/pom.xml"

usage() {
    cat <<'USAGE'
Usage: ./devBuildAndRun.sh [options]

Options:
  -BackendPom, --backend-pom <path>            Backend pom.xml path relative to repo root (default: backend/pom.xml)
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
        -BackendPom|--backend-pom)
            require_value "$1" "${2:-}"
            BACKEND_POM="$2"
            shift 2
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
BACKEND_POM_PATH="$SCRIPT_ROOT/$BACKEND_POM"
BACKEND_DIR_PATH="$(dirname "$BACKEND_POM_PATH")"

if [[ ! -f "$BACKEND_POM_PATH" ]]; then
    echo "Backend pom.xml not found: $BACKEND_POM_PATH" >&2
    exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is not available on PATH." >&2
    exit 1
fi

echo "[1/2] Building backend (includes frontend bundle)..."
(
    cd "$BACKEND_DIR_PATH"
    mvn clean package -DskipTests
)

echo "[2/2] Starting backend..."
(
    cd "$BACKEND_DIR_PATH"
    mvn spring-boot:run
)

