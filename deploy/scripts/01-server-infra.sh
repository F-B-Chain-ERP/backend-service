#!/usr/bin/env bash
# ==============================================================================
# BƯỚC 1: Khởi chạy hạ tầng (PostgreSQL 16 + Redis 7) trên Server
# Chạy trực tiếp trên server: ssh root@163.61.72.183
# Lệnh chạy: sudo bash /opt/ERP-UTT/backend-service/deploy/scripts/01-server-infra.sh
# ==============================================================================

set -e

echo "=========================================================="
echo "  [BƯỚC 1] KHỞI CHẠY HẠ TẦNG POSTGRESQL & REDIS TRÊN SERVER"
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$SCRIPT_DIR"

echo "▶ 1. Pull Docker images cho PostgreSQL và Redis..."
docker pull postgres:16-alpine
docker pull redis:7-alpine

echo "▶ 2. Khởi chạy container PostgreSQL và Redis qua infra.yml..."
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
echo "=========================================================="
