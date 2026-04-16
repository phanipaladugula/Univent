$ErrorActionPreference = "Stop"

# Define paths for the whole project
$root = Split-Path -Parent $PSScriptRoot
$infraEnv = Join-Path $root "infrastructure\.env"
$springEnv = Join-Path $root "Backend\Springboot\Univent-Backend\.env"
$goEnv = Join-Path $root "Backend\Go\edge-service\.env"
$pythonEnv = Join-Path $root "Backend\Python\ai-worker\.env"

# Templates
$infraTemplate = Join-Path $root "infrastructure\.env.example"
$springTemplate = Join-Path $root "Backend\Springboot\Univent-Backend\.env.example"
$goTemplate = Join-Path $root "Backend\Go\edge-service\.env.example"
$pythonTemplate = Join-Path $root "Backend\Python\ai-worker\.env.example"

function New-HexSecret {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

# 1. Generate SHARED secrets once to ensure consistency
$jwtSecret = New-HexSecret
$postgresPassword = New-HexSecret -Bytes 16
$encryptionSecret = New-HexSecret -Bytes 16 # For ID card encryption

# 2. Function to process a template and save it
function Create-EnvFromTemplate {
    param($TemplatePath, $OutputPath, $Jwt, $DbPass, $Encrypt)
    
    if (Test-Path $TemplatePath) {
        $content = Get-Content $TemplatePath
        # Replace shared placeholders
        $content = $content -replace "JWT_SECRET=.*", "JWT_SECRET=$Jwt"
        $content = $content -replace "DB_PASSWORD=.*", "DB_PASSWORD=$DbPass"
        $content = $content -replace "POSTGRES_PASSWORD=.*", "POSTGRES_PASSWORD=$DbPass"
        $content = $content -replace "ENCRYPTION_SECRET=.*", "ENCRYPTION_SECRET=$Encrypt"
        
        # Handle Go/Python connection string style replacements if they exist
        $content = $content -replace "YOUR_PASSWORD", $DbPass
        $content = $content -replace "YOUR_JWT_SECRET_HEX_HERE", $Jwt

        Set-Content -Path $OutputPath -Value $content
        Write-Host "✅ Created $OutputPath" -ForegroundColor Green
    } else {
        Write-Warning "Skipped: Template not found at $TemplatePath"
    }
}

# 3. Create all .env files
Create-EnvFromTemplate $infraTemplate $infraEnv $jwtSecret $postgresPassword $encryptionSecret
Create-EnvFromTemplate $springTemplate $springEnv $jwtSecret $postgresPassword $encryptionSecret
Create-EnvFromTemplate $goTemplate $goEnv $jwtSecret $postgresPassword $encryptionSecret
Create-EnvFromTemplate $pythonTemplate $pythonEnv $jwtSecret $postgresPassword $encryptionSecret

Write-Host "`nNext Steps:" -ForegroundColor Cyan
Write-Host "1. Paste your GEMINI_API_KEY into Spring Boot and Python .env files."
Write-Host "2. Add your Gmail App Password to the Spring Boot .env for OTPs."
Write-Host "3. Run 'docker compose up -d' in the infrastructure folder."