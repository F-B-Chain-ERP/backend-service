#!/usr/bin/env bash
# ==============================================================================
# BƯỚC 7: Khởi chạy Backend Service trên Server
# Chạy trực tiếp trên server: ssh root@163.61.72.183
# Lệnh chạy: sudo bash /opt/ERP-UTT/backend-service/deploy/scripts/03-deploy-app.sh [tag]
# ==============================================================================

set -e

TAG=${1:-latest}
export APP_IMAGE_TAG="$TAG"

echo "=========================================================="
echo "  [BƯỚC 7] KHỞI CHẠY BACKEND SERVICE TRÊN SERVER"
echo "  Tag Image: $TAG"
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$SCRIPT_DIR"

echo "▶ 1. Khởi động lại container Backend Service..."
docker compose -f src/main/docker/app.yml up -d backend-service

echo ""
echo "▶ 2. Trạng thái containers đang chạy..."
docker compose -f src/main/docker/app.yml ps

echo ""
echo "=========================================================="
echo "  BACKEND SERVICE ĐÃ TRIỂN KHAI THÀNH CÔNG!"
echo "=========================================================="
echo "API Endpoint:"
echo "  - Base URL : http://163.61.72.183:8080"
echo "  - Test Login: POST http://163.61.72.183:8080/api/v1/auth/login"
echo "    Headers   : Content-Type: application/json"
echo "    Body      : {\"usernameOrEmail\":\"admin\", \"password\":\"123456789\"}"
echo ""
echo "Xem logs realtime:"
echo "  docker logs -f erp-backend"
echo "=========================================================="
