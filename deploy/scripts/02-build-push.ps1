# ==============================================================================
# BƯỚC 6 (PowerShell): Build Docker Image ở Local và chuyển/load lên Server
# Chạy tại PowerShell từ thư mục gốc ERP-UTT
# Cách dùng: .\backend-service\deploy\scripts\02-build-push.ps1 [-Tag "latest"] [-ServerIp "163.61.72.183"]
# ==============================================================================

param (
    [string]$Tag = "latest",
    [string]$ServerIp = "163.61.72.183",
    [string]$ServerUser = "root"
)

$ErrorActionPreference = "Stop"
$ImageName = "erp-backend:$Tag"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  [BƯỚC 6] BUILD & LOAD IMAGE LÊN SERVER (PowerShell)" -ForegroundColor Cyan
Write-Host "  Image: $ImageName" -ForegroundColor Yellow
Write-Host "  Server Target: $ServerUser@$ServerIp" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

# Xác định thư mục root
$RootDir = "."
if (-not (Test-Path "backend-service") -or -not (Test-Path "core-model")) {
    if ((Test-Path "..\backend-service") -and (Test-Path "..\core-model")) {
        $RootDir = ".."
    } elseif ((Test-Path "..\..\backend-service") -and (Test-Path "..\..\core-model")) {
        $RootDir = "..\.."
    } else {
        Write-Error "Vui lòng chạy script từ thư mục root của dự án ERP-UTT!"
        exit 1
    }
}

Write-Host "`n▶ 1. Build Docker image từ Local..." -ForegroundColor Green
docker build -f backend-service/Dockerfile -t "$ImageName" "$RootDir"

Write-Host "`n▶ 2. Lưu và truyền image lên Server qua SSH..." -ForegroundColor Green
docker save "$ImageName" | ssh "$ServerUser@$ServerIp" "docker load"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  HOÀN THÀNH: Image $ImageName đã được load thành công trên server $ServerIp!" -ForegroundColor Green
Write-Host "  Bước tiếp theo: SSH vào server và chạy script 03-deploy-app.sh" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
