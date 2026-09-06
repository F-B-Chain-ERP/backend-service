#!/bin/bash
# ==============================================================================
# Script Thiết lập Tường lửa UFW & Bảo mật Port Docker cho ERP-UTT
# Chạy trên Server VPS Ubuntu 24.04: sudo bash 05-harden-firewall.sh
# ==============================================================================

set -e

echo "========================================================"
echo " [BƯỚC 1/3] Cấu hình chính sách mặc định UFW Firewall"
echo "========================================================"

# Đặt chính sách mặc định: Chặn toàn bộ incoming, cho phép outgoing
ufw default deny incoming
ufw default allow outgoing

# Mở các cổng công khai cần thiết cho dịch vụ
echo "Cho phép Port 22 (SSH)..."
ufw allow 22/tcp comment 'SSH Remote Access'

echo "Cho phép Port 80 (HTTP redirect)..."
ufw allow 80/tcp comment 'HTTP Web Redirect'

echo "Cho phép Port 443 (HTTPS production)..."
ufw allow 443/tcp comment 'HTTPS Web SSL'

echo "Cho phép Port 5432 (PostgreSQL Database)..."
ufw allow 5432/tcp comment 'PostgreSQL Remote Dev/Admin'

echo "Cho phép Port 6379 (Redis Cache)..."
ufw allow 6379/tcp comment 'Redis Remote Dev/Admin'

echo ""
echo "========================================================"
echo " [BƯỚC 2/3] Bảo vệ Docker Ports (Backend 8080 nội bộ)"
echo "========================================================"

# Đảm bảo Spring Boot :8080 chỉ truy cập qua Nginx Reverse Proxy (127.0.0.1)
# Backend trong app.yml đã được gán trực tiếp 127.0.0.1:8080:8080

echo ""
echo "========================================================"
echo " [BƯỚC 3/3] Kích hoạt và kiểm tra trạng thái UFW Firewall"
echo "========================================================"

# Kích hoạt tường lửa không cần prompt xác nhận
ufw --force enable
ufw reload

echo ""
echo "=== Trạng thái UFW sau khi cấu hình ==="
ufw status verbose

echo ""
echo "========================================================"
echo "✅ HOÀN TẤT THIẾT LẬP TƯỜNG LỬA BẢO VỆ PRODUCTION!"
echo " - Port 22, 80, 443: MỞ CÔNG KHAI (SSH & Web/API HTTPS)"
echo " - Port 5432, 6379: MỞ CHO DEVELOPER / QUẢN TRỊ"
echo " - Port 8080 (Spring Boot): BẢO VỆ NỘI BỘ QUA REVERSE PROXY NGINX"
echo "========================================================"
