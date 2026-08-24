#!/usr/bin/env bash
# ==============================================================================
# BƯỚC 4: Khởi chạy Trạm Giám sát (Prometheus, Grafana, Exporters) trên Server
# Chạy trực tiếp trên server: ssh root@<SERVER_IP>
# Lệnh chạy: sudo bash deploy/scripts/04-start-monitoring.sh
# ==============================================================================

set -e

echo "=========================================================="
echo "  [BƯỚC 4] KHỞI CHẠY TRẠM GIÁM SÁT MONITORING STACK"
echo "=========================================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$SCRIPT_DIR"

echo "▶ 1. Khởi chạy toàn bộ hệ thống Monitoring (Prometheus + Grafana + Exporters)..."
docker compose -f src/main/docker/monitoring.yml up -d

echo ""
echo "▶ 2. Kiểm tra trạng thái containers giám sát..."
docker compose -f src/main/docker/monitoring.yml ps

echo ""
echo "=========================================================="
echo "  TRẠM GIÁM SÁT ĐÃ HOẠT ĐỘNG! (BẢO MẬT: BIND 127.0.0.1)"
echo "=========================================================="
echo "Các cổng đều được bind vào 127.0.0.1 để đảm bảo an toàn."
echo ""
echo "Cách truy cập Grafana & Prometheus an toàn:"
echo "1. Qua SSH Tunnel từ máy Local (Khuyến nghị):"
echo "   ssh -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 root@<SERVER_IP>"
echo "   Sau đó mở trình duyệt: http://localhost:3000"
echo "   User: admin | Password: admin123456"
echo ""
echo "2. Hoặc cấu hình Nginx Reverse Proxy kèm Basic Auth."
echo "=========================================================="
