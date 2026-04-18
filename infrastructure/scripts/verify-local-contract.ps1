# Local contract checks for Univent (run from repo root or infrastructure/).
# Requires: Docker Compose stack up (e.g. docker compose up -d from infrastructure/).
$ErrorActionPreference = "Stop"
$infra = Split-Path -Parent $PSScriptRoot
Set-Location $infra

Write-Host "=== docker compose config (build contexts) ===" -ForegroundColor Cyan
docker compose config 2>&1 | Select-Object -First 80

Write-Host "`n=== Spring actuator mappings (first 2000 chars) ===" -ForegroundColor Cyan
Write-Host "Note: Spring permits anonymous GET /actuator/mappings and sets management.endpoint.mappings.access=unrestricted."
try {
  $m = Invoke-WebRequest -Uri "http://localhost:8080/actuator/mappings" -UseBasicParsing -TimeoutSec 15
  Write-Host "Status: $($m.StatusCode)"
  if ($m.Content.Length -gt 2000) {
    $m.Content.Substring(0, 2000) + "`n... (truncated)"
  } else {
    $m.Content
  }
} catch {
  Write-Warning "Mappings request failed (is spring-boot on :8080?). $_"
}

Write-Host "`n=== GET /api/v1/news ===" -ForegroundColor Cyan
try {
  Invoke-WebRequest -Uri "http://localhost:8080/api/v1/news?page=0&size=5" -UseBasicParsing -TimeoutSec 15 | ForEach-Object { $_.StatusCode; $_.Content.Substring(0, [Math]::Min(800, $_.Content.Length)) }
} catch {
  Write-Warning "News request failed: $_"
}

Write-Host "`n=== GET python-ai /ready ===" -ForegroundColor Cyan
try {
  Invoke-WebRequest -Uri "http://localhost:8000/ready" -UseBasicParsing -TimeoutSec 15 | ForEach-Object { $_.StatusCode; $_.Content }
} catch {
  Write-Warning "AI ready check failed: $_"
}

Write-Host "`nDone." -ForegroundColor Green
