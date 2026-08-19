#!/usr/bin/env bash
# ==============================================================================
# Script tự động cài đặt Docker, cấu hình Firewall và triển khai ERP Backend
# Áp dụng cho: Ubuntu 24.04 LTS (IP: 163.61.72.183)
# ==============================================================================

set -e

echo "=========================================================="
echo "  BẮT ĐẦU CÀI ĐẶT & TRIỂN KHAI HỆ THỐNG ERP UTT TRÊN SERVER"
echo "=========================================================="

# 1. Kiểm tra quyền root
if [ "$EUID" -ne 0 ]; then
  echo "[!] Vui lòng chạy script với quyền root hoặc dùng sudo: sudo bash $0"
  exit 1
fi

# 2. Cập nhật hệ thống và cài đặt gói bổ trợ
echo "[1/5] Cập nhật hệ thống & cài đặt packages cần thiết..."
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release ufw git

# 3. Cài đặt Docker và Docker Compose Plugin chính thức
if ! command -v docker &> /dev/null; then
    echo "[2/5] Cài đặt Docker Engine & Docker Compose..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -y
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable docker
    systemctl start docker
    echo "[OK] Docker installed successfully."
else
    echo "[2/5] Docker đã được cài đặt, bỏ qua bước cài đặt."
fi

# 4. Cấu hình Firewall (UFW)
echo "[3/5] Cấu hình Firewall UFW (Mở cổng SSH, Backend, DB, Redis)..."
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 8080/tcp comment 'ERP Backend Service'
ufw allow 5432/tcp comment 'PostgreSQL (Dev/Test remote access)'
ufw allow 6379/tcp comment 'Redis (Dev/Test remote access)'
ufw --force enable
echo "[OK] Firewall configured."

# 5. Di chuyển đến thư mục backend-service
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

# 6. Khởi chạy Docker Compose qua src/main/docker/app.yml
echo "[4/4] Khởi động các container: PostgreSQL, Redis, Backend Service..."
docker compose -f src/main/docker/app.yml down || true
docker compose -f src/main/docker/app.yml up -d --build

echo "=========================================================="
echo "  TRIỂN KHAI HOÀN TẤT!"
echo "=========================================================="
docker compose ps

echo ""
echo "THÔNG TIN KẾT NỐI:"
echo "----------------------------------------------------------"
echo "1. PostgreSQL (Dev/Test):"
echo "   - Host: 163.61.72.183"
echo "   - Port: 5432"
echo "   - DB:   erp_dev"
echo "   - User: erp_user"
echo "   - Pass: erp123456@"
echo ""
echo "2. Redis (Dev/Test):"
echo "   - Host: 163.61.72.183"
echo "   - Port: 6379"
echo "   - Pass: erp_redis_2026"
echo ""
echo "3. Backend API (Tester):"
echo "   - Endpoint: http://163.61.72.183:8080"
echo "   - Test Login: POST http://163.61.72.183:8080/api/v1/auth/login"
echo "     Body: {\"usernameOrEmail\":\"admin\", \"password\":\"123456789\"}"
echo "=========================================================="
