#!/usr/bin/env bash
# ==============================================================================
# Script tự động cài đặt Docker, cấu hình Firewall và khởi tạo Hạ tầng (PostgreSQL, Redis)
# Áp dụng cho: Ubuntu 24.04 LTS (IP: 163.61.72.183)
# ==============================================================================

set -e

echo "=========================================================="
echo "  BẮT ĐẦU CÀI ĐẶT SERVER & KHỞI TẠO HẠ TẦNG ERP UTT"
echo "=========================================================="

# 1. Kiểm tra quyền root
if [ "$EUID" -ne 0 ]; then
  echo "[!] Vui lòng chạy script với quyền root hoặc dùng sudo: sudo bash $0"
  exit 1
fi

# 2. Cập nhật hệ thống và cài đặt gói bổ trợ
echo "[1/4] Cập nhật hệ thống & cài đặt packages cần thiết..."
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release ufw git

# 3. Cài đặt Docker và Docker Compose Plugin chính thức
if ! command -v docker &> /dev/null; then
    echo "[2/4] Cài đặt Docker Engine & Docker Compose..."
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
    echo "[2/4] Docker đã được cài đặt, bỏ qua bước cài đặt."
fi

# 4. Cấu hình Firewall (UFW)
echo "[3/4] Cấu hình Firewall UFW (Mở cổng SSH, Backend, DB, Redis)..."
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw allow 8080/tcp comment 'ERP Backend Service'
ufw allow 5432/tcp comment 'PostgreSQL (Dev/Test remote access)'
ufw allow 6379/tcp comment 'Redis (Dev/Test remote access)'
ufw --force enable
echo "[OK] Firewall configured."

# 5. Khởi chạy PostgreSQL & Redis qua script hạ tầng
echo "[4/4] Khởi động hạ tầng PostgreSQL + Redis..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
chmod +x "$SCRIPT_DIR/scripts/"*.sh 2>/dev/null || true
bash "$SCRIPT_DIR/scripts/01-server-infra.sh"

echo "=========================================================="
echo "  CÀI ĐẶT SERVER & HẠ TẦNG HOÀN TẤT!"
echo "  Bước tiếp theo từ Local:"
echo "  1. Test kết nối DB (5432) & Redis (6379)"
echo "  2. Chạy SQL tạo tables thủ công vào PostgreSQL"
echo "  3. Chạy backend local test API"
echo "  4. Build image local -> nạp lên server -> chạy script 03-deploy-app.sh"
echo "=========================================================="
