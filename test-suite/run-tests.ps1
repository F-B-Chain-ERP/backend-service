# ==============================================================================
# SCRIPT CHAY BO TEST SUITE ERP-UTT TREN WINDOWS
# Tu dong kiem tra: Python Local, Docker Desktop hoac huong dan SSH VPS
# ==============================================================================

param (
    [string]$Target = "http://163.61.72.183",
    [string]$Action = "security",
    [string]$DbHost = "163.61.72.183",
    [int]$DbPort = 5432,
    [string]$DbName = "erp_dev",
    [string]$DbUser = "postgres",
    [string]$DbPass = "postgres",
    [string]$ServerIp = "163.61.72.183",
    [string]$ServerUser = "root"
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  ERP-UTT AUTOMATED TEST RUNNER (POWERSHELL)" -ForegroundColor Cyan
Write-Host "  Target URL: $Target" -ForegroundColor Yellow
Write-Host "  Action:     $Action" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# Kiem tra Docker Daemon
$DockerRunning = $false
try {
    docker info > $null 2>&1
    if ($LASTEXITCODE -eq 0) {
        $DockerRunning = $true
    }
} catch {
    $DockerRunning = $false
}

# Kiem tra Python Local
$PythonCmd = $null
if (Get-Command python -ErrorAction SilentlyContinue) {
    $PythonCmd = "python"
} elseif (Get-Command py -ErrorAction SilentlyContinue) {
    $PythonCmd = "py"
} elseif (Get-Command python3 -ErrorAction SilentlyContinue) {
    $PythonCmd = "python3"
}

# Thong bao huong dan khi chua co Docker Desktop hoac Python
function Show-Environment-Help {
    Write-Host ""
    Write-Host "[CANH BAO] Docker Desktop chua duoc bat hoac chua co Python tren may Windows!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Chon 1 trong 2 cach sau de thuc hien test:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "--- CACH 1 (KHUYEN NGHI): CHAY TRUC TIEP TREN VPS SERVER QUA SSH ---" -ForegroundColor Green
    Write-Host "He thong da duoc deploy tai VPS ($ServerIp). VPS da co san Python3 & Docker:" -ForegroundColor Gray
    Write-Host "  1. Dong bo folder test-suite len server:" -ForegroundColor White
    Write-Host "     .\backend-service\test-suite\run-tests.ps1 -Action sync-server" -ForegroundColor Yellow
    Write-Host "  2. SSH vao server va chay ngay:" -ForegroundColor White
    Write-Host "     ssh $ServerUser@$ServerIp" -ForegroundColor Yellow
    Write-Host "     cd /opt/ERP-UTT/backend-service/test-suite" -ForegroundColor Yellow
    Write-Host "     chmod +x run-tests.sh" -ForegroundColor Yellow
    Write-Host "     ./run-tests.sh security http://127.0.0.1:8080" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "--- CACH 2: BAT DOCKER DESKTOP TREN MAY WINDOWS ---" -ForegroundColor Green
    Write-Host "  1. Mo ung dung 'Docker Desktop' tren Windows va doi Docker Engine khoi dong." -ForegroundColor Gray
    Write-Host "  2. Sau do chay lai lenh nay:" -ForegroundColor White
    Write-Host "     .\backend-service\test-suite\run-tests.ps1 -Target '$Target' -Action '$Action'" -ForegroundColor Yellow
    Write-Host ""
}

# Ham chay Python Script
function Execute-Python-Script ([string]$SubDir, [string]$ScriptFile) {
    $workDir = Join-Path $ScriptDir $SubDir
    Write-Host "`n[RUN] Dang chay script: $SubDir/$ScriptFile ..." -ForegroundColor Green

    if ($PythonCmd) {
        Write-Host "  -> Su dung $PythonCmd cuc bo tren Windows" -ForegroundColor Gray
        Push-Location $workDir
        try {
            & $PythonCmd -m pip install -q -r requirements.txt
            $env:BASE_URL = $Target
            $env:DB_HOST = $DbHost
            $env:DB_PORT = $DbPort
            $env:DB_NAME = $DbName
            $env:DB_USERNAME = $DbUser
            $env:DB_PASSWORD = $DbPass
            & $PythonCmd $ScriptFile
        } finally {
            Pop-Location
        }
    } elseif ($DockerRunning) {
        Write-Host "  -> Su dung Docker container python:3.12-slim" -ForegroundColor Gray
        docker run --rm -i `
            -v "${workDir}:/app" `
            -w /app `
            -e "BASE_URL=$Target" `
            -e "DB_HOST=$DbHost" `
            -e "DB_PORT=$DbPort" `
            -e "DB_NAME=$DbName" `
            -e "DB_USERNAME=$DbUser" `
            -e "DB_PASSWORD=$DbPass" `
            python:3.12-slim `
            sh -c "pip install -q -r requirements.txt && python $ScriptFile"
    } else {
        Show-Environment-Help
        exit 1
    }
}

# Ham chay k6 Script
function Execute-K6-Script ([string]$ScriptFile) {
    $localK6Dir = Join-Path $ScriptDir "k6"
    Write-Host "`n[RUN] Dang chay k6: $ScriptFile ..." -ForegroundColor Green

    if (Get-Command k6 -ErrorAction SilentlyContinue) {
        Write-Host "  -> Su dung k6 native tren Windows" -ForegroundColor Gray
        Push-Location $localK6Dir
        try {
            k6 run -e "BASE_URL=$Target" $ScriptFile
        } finally {
            Pop-Location
        }
    } elseif ($DockerRunning) {
        Write-Host "  -> Su dung Docker container grafana/k6" -ForegroundColor Gray
        docker run --rm -i `
            -v "${localK6Dir}:/scripts" `
            -e "BASE_URL=$Target" `
            grafana/k6 run "/scripts/$ScriptFile"
    } else {
        Show-Environment-Help
        exit 1
    }
}

# Dong bo test-suite len VPS
function Sync-To-Server {
    Write-Host "`n[SYNC] Dang dong bo thu muc test-suite len Server $ServerUser@$ServerIp ..." -ForegroundColor Green
    $dest = "/opt/ERP-UTT/backend-service/"
    scp -r "$ScriptDir" "${ServerUser}@${ServerIp}:${dest}"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Dong bo thanh cong len $dest/test-suite!" -ForegroundColor Green
        Write-Host "Ban co the SSH vao server va chay ngay:" -ForegroundColor Cyan
        Write-Host "  ssh $ServerUser@$ServerIp" -ForegroundColor Yellow
        Write-Host "  cd /opt/ERP-UTT/backend-service/test-suite" -ForegroundColor Yellow
        Write-Host "  chmod +x run-tests.sh" -ForegroundColor Yellow
        Write-Host "  ./run-tests.sh security http://127.0.0.1:8080" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Dong bo qua SCP that bai. Vui long kiem tra ket noi SSH." -ForegroundColor Red
    }
}

switch ($Action.ToLower()) {
    "sync-server" {
        Sync-To-Server
    }
    "seeder" {
        Execute-Python-Script "seeder" "generate_large_data.py"
    }
    "security" {
        Write-Host "`n--- BAT DAU CHAY SECURITY SUITE ---" -ForegroundColor Magenta
        Execute-Python-Script "security" "test_rate_limit.py"
        Execute-Python-Script "security" "test_jwt_tampering.py"
        Execute-Python-Script "security" "test_idor_datascope.py"
        Execute-Python-Script "security" "test_sqli_fuzzing.py"
        Execute-Python-Script "security" "test_dos_resilience.py"
    }
    "baseline" {
        Execute-K6-Script "01_baseline_load.js"
    }
    "stress" {
        Execute-K6-Script "02_stress_test.js"
    }
    "spike" {
        Execute-K6-Script "03_spike_test.js"
    }
    "race" {
        Execute-K6-Script "05_race_condition_stock.js"
    }
    "all" {
        Execute-Python-Script "seeder" "generate_large_data.py"
        Execute-Python-Script "security" "test_rate_limit.py"
        Execute-Python-Script "security" "test_jwt_tampering.py"
        Execute-Python-Script "security" "test_idor_datascope.py"
        Execute-Python-Script "security" "test_sqli_fuzzing.py"
        Execute-K6-Script "01_baseline_load.js"
        Execute-K6-Script "05_race_condition_stock.js"
        Execute-K6-Script "02_stress_test.js"
    }
    Default {
        Write-Host "Action khong hop le: '$Action'." -ForegroundColor Red
        Write-Host "Cac action hop le: security, baseline, stress, spike, race, seeder, sync-server, all" -ForegroundColor Yellow
    }
}
