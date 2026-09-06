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

echo "Cho phép Port 1194/UDP (OpenVPN Server)..."
ufw allow 1194/udp comment 'OpenVPN Tunnel'

# Cho phép toàn bộ lưu lượng trên card mạng ảo OpenVPN (tun0 / tun+)
echo "Cho phép lưu lượng từ interface VPN tun+..."
ufw allow in on tun+ comment 'Allow all traffic from OpenVPN clients'

echo ""
echo "========================================================"
echo " [BƯỚC 2/3] Bảo vệ Docker Ports (PostgreSQL: 5432, Redis: 6379, App: 8080)"
echo " Chặn triệt để bypass iptables của Docker từ Internet công cộng"
echo "========================================================"

# Docker mặc định tự chèn iptables rule và bỏ qua UFW INPUT thông thường.
# Để giải quyết triệt để, chúng ta cấu hình chuỗi DOCKER-USER trong /etc/ufw/after.rules

AFTER_RULES="/etc/ufw/after.rules"
BACKUP_RULES="/etc/ufw/after.rules.bak-$(date +%Y%m%d%H%M%S)"
cp "$AFTER_RULES" "$BACKUP_RULES"

# Kiểm tra xem đã có cấu hình DOCKER-USER chưa
if grep -q "DOCKER-USER" "$AFTER_RULES"; then
    echo "Chuỗi DOCKER-USER đã tồn tại trong $AFTER_RULES, bỏ qua ghi đè..."
else
    echo "Đang cấu hình chuỗi DOCKER-USER vào cuối $AFTER_RULES..."
    cat << 'EOF' >> "$AFTER_RULES"

# ------------------------------------------------------------------------------
# ERP-UTT: Chặn truy cập Internet công cộng vào các port Docker nội bộ
# Chỉ cho phép từ localhost (lo), mạng nội bộ Docker và VPN client (tun+)
# ------------------------------------------------------------------------------
*filter
:DOCKER-USER - [0:0]
# Cho phép kết nối đã thiết lập từ trước
-A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
# Cho phép từ loopback (localhost)
-A DOCKER-USER -i lo -j ACCEPT
# Cho phép từ interface OpenVPN (tun+)
-A DOCKER-USER -i tun+ -j ACCEPT
# Cho phép nội bộ mạng docker
-A DOCKER-USER -s 172.16.0.0/12 -j ACCEPT

# CHẶN truy cập từ Internet vào PostgreSQL (5432), Redis (6379), Spring Boot (8080)
-A DOCKER-USER -p tcp -m multiport --dports 5432,6379,8080 -j DROP

# Cho phép các container khác chuyển tiếp bình thường
-A DOCKER-USER -j RETURN
COMMIT
EOF
fi

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
echo " - Port 22, 80, 443, 1194/UDP: MỞ CÔNG KHAI"
echo " - Port 5432 (Postgres), 6379 (Redis), 8080 (Spring Boot): ĐƯỢC BẢO VỆ CHẶT CHẼ"
echo "   (Chỉ máy kết nối OpenVPN hoặc localhost VPS mới truy cập được)"
echo "========================================================"
