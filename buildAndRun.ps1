param(
    [string]$ProjectName = "mcmsm",
    [string]$ComposeFile = "docker-compose.yml",
    [string]$BackendPom = "backend/pom.xml",
    [string]$FrontendDir = "frontend",
    [switch]$SkipFrontendInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$composePath = Join-Path $scriptRoot $ComposeFile
$backendPomPath = Join-Path $scriptRoot $BackendPom
$backendDirPath = Split-Path -Parent $backendPomPath
$frontendPath = Join-Path $scriptRoot $FrontendDir
$frontendPackageJsonPath = Join-Path $frontendPath "package.json"

if (-not (Test-Path -Path $composePath)) {
    throw "Compose file not found: $composePath"
}

if (-not (Test-Path -Path $backendPomPath)) {
    throw "Backend pom.xml not found: $backendPomPath"
}

if (-not (Test-Path -Path $frontendPackageJsonPath)) {
    throw "Frontend package.json not found: $frontendPackageJsonPath"
}

docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose is not available on PATH."
}

mvn -v *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Maven is not available on PATH."
}

npm -v *> $null
if ($LASTEXITCODE -ne 0) {
    throw "npm is not available on PATH."
}

$composeArgs = @("-p", $ProjectName, "-f", $composePath)

Write-Host "[1/6] Checking Postgres container state in project '$ProjectName'..."
$postgresContainerId = (docker compose @composeArgs ps -q postgres).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Failed to inspect Postgres container."
}

if ([string]::IsNullOrWhiteSpace($postgresContainerId)) {
    Write-Host "[2/6] Postgres is not running. Starting Postgres..."
    docker compose @composeArgs up -d postgres
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start Postgres container."
    }
    $postgresContainerId = (docker compose @composeArgs ps -q postgres).Trim()
} else {
    $postgresRunning = (docker inspect -f "{{.State.Running}}" $postgresContainerId).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect Postgres runtime state."
    }

    if ($postgresRunning -ne "true") {
        Write-Host "[2/6] Postgres container exists but is stopped. Starting Postgres..."
        docker compose @composeArgs up -d postgres
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to start Postgres container."
        }
        $postgresContainerId = (docker compose @composeArgs ps -q postgres).Trim()
    } else {
        Write-Host "[2/6] Postgres is already running."
    }
}

if ([string]::IsNullOrWhiteSpace($postgresContainerId)) {
    throw "Could not resolve Postgres container ID after startup."
}

Write-Host "[3/6] Waiting for Postgres health check to report healthy..."
$maxAttempts = 24
for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    $health = (docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}" $postgresContainerId).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to inspect Postgres health status."
    }

    if ($health -eq "healthy") {
        break
    }

    if ($attempt -eq $maxAttempts) {
        throw "Postgres did not become healthy in time. Last status: $health"
    }

    Start-Sleep -Seconds 2
}

Write-Host "[4/6] Building backend..."
Push-Location -Path $backendDirPath
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed."
    }
} finally {
    Pop-Location
}

Write-Host "[5/6] Building frontend..."
Push-Location -Path $frontendPath
try {
    if (-not $SkipFrontendInstall) {
        npm install
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend dependency install failed."
        }
    }

    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed."
    }
} finally {
    Pop-Location
}

Write-Host "[6/6] Starting backend and frontend..."

$escapedBackendDir = $backendDirPath.Replace("'", "''")
$escapedFrontendDir = $frontendPath.Replace("'", "''")

$backendProcess = Start-Process -FilePath "powershell.exe" -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$escapedBackendDir'; mvn spring-boot:run"
) -PassThru

$frontendProcess = Start-Process -FilePath "powershell.exe" -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$escapedFrontendDir'; npm run dev"
) -PassThru

Write-Host "Backend started in new PowerShell window (PID: $($backendProcess.Id))."
Write-Host "Frontend started in new PowerShell window (PID: $($frontendProcess.Id))."
Write-Host "Postgres is running via Docker Compose project '$ProjectName'."

