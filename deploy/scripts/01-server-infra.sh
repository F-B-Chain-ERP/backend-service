#!/usr/bin/env bash
# ==============================================================================
# BƯỚC 1: Khởi chạy hạ tầng (PostgreSQL 16 + Redis 7 + Minio) trên Server
# Chạy trực tiếp trên server: ssh root@163.61.72.183
# Lệnh chạy: sudo bash /opt/ERP-UTT/backend-service/deploy/scripts/01-server-infra.sh
# ==============================================================================

set -e

echo "=========================================================="
echo "  [BƯỚC 1] KHỞI CHẠY HẠ TẦNG POSTGRESQL - REDIS - MINIO TRÊN SERVER"
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$SCRIPT_DIR"

echo "▶ 1. Pull Docker images cho PostgreSQL, Redis và MinIO..."
docker pull postgres:16-alpine
docker pull redis:7-alpine
docker pull minio/minio:RELEASE.2024-08-29T01-40-52Z

echo "▶ 2. Khởi chạy container PostgreSQL, Redis và MinIO qua infra.yml..."
docker compose -f src/main/docker/infra.yml up -d

echo ""
echo "▶ 3. Kiểm tra trạng thái containers..."
docker compose -f src/main/docker/infra.yml ps

echo ""
echo "=========================================================="
echo "  HẠ TẦNG ĐÃ SẴN SÀNG!"
echo "=========================================================="
echo "1. PostgreSQL:"
echo "   - Host: 163.61.72.183"
echo "   - Port: 5432"
echo "   - Database: erp_dev"
echo "   - Username: erp_user"
echo "   - Password: erp123456@"
echo ""
echo "2. Redis:"
echo "   - Host: 163.61.72.183"
echo "   - Port: 6379"
echo "   - Password: erp_redis_2026"
echo ""
echo "3. MinIO (Object Storage):"
echo "   - Web Console: https://erp-utt.duckdns.org:9001"
echo "   - Storage S3: https://erp-utt.duckdns.org/storage/ (hoặc http://163.61.72.183:9000)"
echo "   - Root User: erp_minio"
echo "   - Root Password: erp123456@"
echo "=========================================================="
