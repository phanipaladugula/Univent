$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$envExample = Join-Path $root "infrastructure\.env.example"
$envFile = Join-Path $root "infrastructure\.env"

if (-not (Test-Path $envExample)) {
    throw "Missing environment template at $envExample"
}

if (Test-Path $envFile) {
    throw "The file '$envFile' already exists. Move or remove it before bootstrapping a new one."
}

function New-HexSecret {
    param([int]$Bytes = 32)

    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

$jwtSecret = New-HexSecret
$postgresPassword = New-HexSecret -Bytes 16
$minioPassword = New-HexSecret -Bytes 16
$grafanaPassword = New-HexSecret -Bytes 16

$content = Get-Content $envExample
$content = $content -replace "POSTGRES_PASSWORD=change-me", "POSTGRES_PASSWORD=$postgresPassword"
$content = $content -replace "JWT_SECRET=replace-with-64-char-hex-secret", "JWT_SECRET=$jwtSecret"
$content = $content -replace "MINIO_ROOT_PASSWORD=change-me", "MINIO_ROOT_PASSWORD=$minioPassword"
$content = $content -replace "GF_SECURITY_ADMIN_PASSWORD=change-me", "GF_SECURITY_ADMIN_PASSWORD=$grafanaPassword"

Set-Content -Path $envFile -Value $content

Write-Host "Created $envFile"
Write-Host "Next steps:"
Write-Host "1. Set GEMINI_API_KEY in infrastructure/.env"
Write-Host "2. Review generated passwords and rotate them for production secrets management"
Write-Host "3. Start the stack with: docker compose --profile monitoring up --build"
