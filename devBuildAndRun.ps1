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

$jarFile = Get-ChildItem -Path "$backendDirPath\target" -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*-sources*" -and $_.Name -notlike "*-javadoc*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jarFile) {
    throw "No JAR found in $backendDirPath\target"
}

Write-Host "[2/2] Starting $($jarFile.Name)..."

java -jar $jarFile.FullName
