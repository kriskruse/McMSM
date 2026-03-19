param(
    [string]$BackendPom = "backend/pom.xml"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendPomPath = Join-Path $scriptRoot $BackendPom
$backendDirPath = Split-Path -Parent $backendPomPath

if (-not (Test-Path -Path $backendPomPath)) {
    throw "Backend pom.xml not found: $backendPomPath"
}

mvn -v *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Maven is not available on PATH."
}

Write-Host "[1/2] Building backend (includes frontend bundle)..."
Push-Location -Path $backendDirPath
try {
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed."
    }
} finally {
    Pop-Location
}

Write-Host "[2/2] Starting backend..."

mvn spring-boot:run
