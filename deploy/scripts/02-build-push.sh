#!/usr/bin/env bash
# ==============================================================================
# BƯỚC 6: Build Docker Image ở Local và chuyển/load lên Server
# Chạy tại thư mục gốc monorepo (ERP-UTT) trên máy Local
# Cách dùng: bash backend-service/deploy/scripts/02-build-push.sh [tag] [server_ip]
# Ví dụ:     bash backend-service/deploy/scripts/02-build-push.sh latest 163.61.72.183
# ==============================================================================

set -e

TAG=${1:-latest}
SERVER_IP=${2:-163.61.72.183}
SERVER_USER=${3:-root}
IMAGE_NAME="erp-backend:$TAG"

echo "=========================================================="
echo "  [BƯỚC 6] BUILD & LOAD IMAGE LÊN SERVER"
echo "  Image: $IMAGE_NAME"
echo "  Server Target: $SERVER_USER@$SERVER_IP"
echo "=========================================================="

# Chuyển về thư mục root của monorepo nếu đang đứng ở thư mục con
if [ -d "backend-service" ] && [ -d "core-model" ]; then
  ROOT_DIR="."
elif [ -d "../backend-service" ] && [ -d "../core-model" ]; then
  ROOT_DIR=".."
elif [ -d "../../backend-service" ] && [ -d "../../core-model" ]; then
  ROOT_DIR="../.."
else
  echo "[!] Vui lòng chạy script từ thư mục root của dự án ERP-UTT!"
  exit 1
fi

echo "▶ 1. Build Docker image từ Local..."
docker build -f backend-service/Dockerfile -t "$IMAGE_NAME" "$ROOT_DIR"

echo "▶ 2. Lưu và truyền trực tiếp image lên Server qua SSH..."
docker save "$IMAGE_NAME" | ssh "$SERVER_USER@$SERVER_IP" "docker load"

echo ""
echo "=========================================================="
echo "  HOÀN THÀNH: Image $IMAGE_NAME đã được load thành công trên server $SERVER_IP!"
echo "  Bước tiếp theo: SSH vào server và chạy script 03-deploy-app.sh"
echo "=========================================================="
