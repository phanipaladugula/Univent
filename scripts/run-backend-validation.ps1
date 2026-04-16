$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

function Assert-CommandAvailable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CommandName,
        [Parameter(Mandatory = $true)]
        [string]$InstallHint
    )

    if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
        throw "Required command '$CommandName' is not available. $InstallHint"
    }
}

Assert-CommandAvailable -CommandName "python" -InstallHint "Install Python 3.12+ and ensure it is available on PATH."

$pythonVersion = python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')"
if ($pythonVersion -notin @("3.12", "3.13")) {
    throw "Python worker validation requires Python 3.12 or 3.13 because several pinned native dependencies do not currently build cleanly on Python $pythonVersion. Use the Docker image or a Python 3.12 virtual environment."
}

Write-Host "Running Spring Boot tests..."
Push-Location "$root\Backend\Springboot\Univent-Backend"
try {
    .\mvnw.cmd test
} finally {
    Pop-Location
}

Write-Host "Running Python unit tests with coverage..."
Push-Location "$root\Backend\Python\ai-worker"
try {
    python -m pip show pytest | Out-Null
    python -m pytest tests --cov=src --cov-report=term-missing
} finally {
    Pop-Location
}

Assert-CommandAvailable -CommandName "go" -InstallHint "Install Go 1.22+ and ensure it is available on PATH."

Write-Host "Running Go tests with coverage..."
Push-Location "$root\Backend\Go\edge-service"
try {
    go test ./... -coverprofile=coverage.out
} finally {
    Pop-Location
}
