#!/usr/bin/env bash
# ==============================================================================
# SCRIPT ROLLBACK THỦ CÔNG CHO BACKEND SERVICE
# Sử dụng khi cần khôi phục lại phiên bản trước đó ngay lập tức
#
# Cách dùng:
#   bash deploy/scripts/rollback.sh [optional_target_tag] [optional_image_name]
# Ví dụ:
#   bash deploy/scripts/rollback.sh                        # Tự động lấy tag stable gần nhất
#   bash deploy/scripts/rollback.sh sha-abc1234            # Rollback về tag cụ thể
# ==============================================================================

set -eo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_FILE="${PROJECT_DIR}/.last-stable-tag"
STATE_IMAGE_FILE="${PROJECT_DIR}/.last-stable-image"
COMPOSE_FILE="${PROJECT_DIR}/src/main/docker/app.yml"

cd "${PROJECT_DIR}"

TARGET_TAG=${1:-""}
TARGET_IMAGE=${2:-""}

if [ -z "${TARGET_TAG}" ]; then
  if [ -f "${STATE_FILE}" ]; then
    TARGET_TAG=$(cat "${STATE_FILE}")
  else
    echo "❌ Không tìm thấy file lưu mốc stable (${STATE_FILE})."
    echo "👉 Vui lòng chỉ định tag cần rollback: bash $0 <tag_name>"
    exit 1
  fi
fi

if [ -z "${TARGET_IMAGE}" ]; then
  if [ -f "${STATE_IMAGE_FILE}" ]; then
    TARGET_IMAGE=$(cat "${STATE_IMAGE_FILE}")
  else
    TARGET_IMAGE="erp-backend"
  fi
fi

echo "=========================================================="
echo "  🔄 BẮT ĐẦU ROLLBACK BACKEND SERVICE"
echo "  Mục tiêu Rollback: ${TARGET_IMAGE}:${TARGET_TAG}"
echo "=========================================================="

IMAGE_NAME="${TARGET_IMAGE}" APP_IMAGE_TAG="${TARGET_TAG}" docker compose -f "${COMPOSE_FILE}" up -d --no-deps backend-service

echo ""
echo "▶ Kiểm tra trạng thái sau rollback..."
sleep 5
docker compose -f "${COMPOSE_FILE}" ps backend-service

echo ""
echo "=========================================================="
echo "  ✅ ĐÃ HOÀN TẤT ROLLBACK VỀ: ${TARGET_IMAGE}:${TARGET_TAG}"
echo "  Xem logs: docker logs -f erp-backend"
echo "=========================================================="
