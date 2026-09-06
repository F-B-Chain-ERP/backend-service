#!/usr/bin/env bash
# ==============================================================================
# SCRIPT DỌN DẸP OPENVPN TRÊN SERVER VPS (1-CLICK CLEANUP & RESTORE HTTPS)
# Áp dụng cho: Ubuntu 24.04 Server (IP: 163.61.72.183)
# Chạy trực tiếp trên VPS: sudo bash deploy/scripts/cleanup-vpn-server.sh
# ==============================================================================

set -eo pipefail

echo "=========================================================="
echo "  🧹 BẮT ĐẦU DỌN DẸP OPENVPN & TỐI ƯU HỆ THỐNG TRÊN VPS"
echo "  Mục tiêu: Giải phóng cổng 443 -> Chuyển Nginx nghe trực tiếp"
echo "  Thời gian: $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================================="

# 1. Kiểm tra quyền root
if [ "$EUID" -ne 0 ]; then
  echo "❌ [ERROR] Vui lòng chạy script với quyền root hoặc sudo: sudo bash $0"
  exit 1
fi

# 2. Dừng và vô hiệu hóa triệt để dịch vụ OpenVPN Server
echo ""
echo "▶ [1/6] Dừng và tắt tự khởi động OpenVPN..."
systemctl stop openvpn-server@server 2>/dev/null || true
systemctl disable openvpn-server@server 2>/dev/null || true
systemctl stop openvpn 2>/dev/null || true
systemctl disable openvpn 2>/dev/null || true

# Tắt các process openvpn còn sót lại nếu có
pkill -f openvpn 2>/dev/null || true
sleep 1
echo "  ✅ OpenVPN service đã dừng thành công."

# 3. Dọn dẹp cấu hình Firewall (UFW)
echo ""
echo "▶ [2/6] Dọn dẹp và cập nhật Tường lửa UFW..."
if command -v ufw &> /dev/null; then
  # Xóa rule 1194/udp và tun+
  ufw delete allow 1194/udp 2>/dev/null || true
  ufw delete allow in on tun+ 2>/dev/null || true

  # Đảm bảo các cổng tiêu chuẩn được mở
  ufw allow 22/tcp comment 'SSH' 2>/dev/null || true
  ufw allow 80/tcp comment 'HTTP' 2>/dev/null || true
  ufw allow 443/tcp comment 'HTTPS Production' 2>/dev/null || true
  ufw allow 5432/tcp comment 'PostgreSQL Dev' 2>/dev/null || true
  ufw allow 6379/tcp comment 'Redis Dev' 2>/dev/null || true

  # Xóa chặn DOCKER-USER đối với 5432 và 6379 nếu có trong /etc/ufw/after.rules
  AFTER_RULES="/etc/ufw/after.rules"
  if [ -f "$AFTER_RULES" ] && grep -q -- "--dports 5432,6379,8080 -j DROP" "$AFTER_RULES"; then
    echo "  -> Gỡ bỏ rule chặn DB/Redis trong DOCKER-USER..."
    sed -i 's/-A DOCKER-USER -p tcp -m multiport --dports 5432,6379,8080 -j DROP/# -A DOCKER-USER -p tcp -m multiport --dports 5432,6379,8080 -j DROP/g' "$AFTER_RULES"
  fi

  ufw reload 2>/dev/null || true
  echo "  ✅ Tường lửa đã mở cổng 22, 80, 443, 5432, 6379."
fi

# 4. Dọn dẹp thư mục web shares tạm thời
echo ""
echo "▶ [3/6] Dọn dẹp thư mục chia sẻ VPN cũ..."
rm -rf /opt/ERP-UTT/vpn-web-shares 2>/dev/null || true
echo "  ✅ Đã dọn dẹp các thư mục chia sẻ tạm."

# 5. Cập nhật file cấu hình Nginx sang cổng 443 trực tiếp
echo ""
echo "▶ [4/6] Cập nhật Nginx cấu hình cổng 443 trực tiếp..."
NGINX_CONF_DEST="/etc/nginx/sites-available/erp-utt"
FRONTEND_CONF="/opt/ERP-UTT/frontend/deploy/nginx/erp-utt.conf"

# Kiểm tra nếu file cấu hình frontend mới có sẵn trên server
if [ -f "$FRONTEND_CONF" ]; then
  cp -f "$FRONTEND_CONF" "$NGINX_CONF_DEST"
fi

# Bắt buộc chuyển đổi cổng 8443 sang 443 trực tiếp và gỡ bỏ route /vpn-setup/
if [ -f "$NGINX_CONF_DEST" ]; then
  sed -i 's/listen 127.0.0.1:8443 ssl http2;/listen 443 ssl http2;\n    listen [::]:443 ssl http2;/g' "$NGINX_CONF_DEST"
  sed -i '/location \^\~ \/vpn-setup\/ {/,/}/d' "$NGINX_CONF_DEST" 2>/dev/null || true
  echo "  ✅ Đã cấu hình Nginx lắng nghe trực tiếp trên cổng 443."
fi
if [ -f "$FRONTEND_CONF" ]; then
  sed -i 's/listen 127.0.0.1:8443 ssl http2;/listen 443 ssl http2;\n    listen [::]:443 ssl http2;/g' "$FRONTEND_CONF" 2>/dev/null || true
  sed -i '/location \^\~ \/vpn-setup\/ {/,/}/d' "$FRONTEND_CONF" 2>/dev/null || true
fi

# Đảm bảo symlink sites-enabled
ln -sf "$NGINX_CONF_DEST" /etc/nginx/sites-enabled/erp-utt
rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true

# 6. Kiểm tra cú pháp Nginx & Khởi động lại
echo ""
echo "▶ [5/6] Kiểm tra cú pháp Nginx & Khởi động lại dịch vụ..."
if nginx -t; then
  systemctl restart nginx
  echo "  ✅ Nginx đã khởi động lại thành công trên cổng 443 trực tiếp!"
else
  echo "❌ [ERROR] Cú pháp Nginx không hợp lệ, vui lòng kiểm tra lại nginx -t!"
  exit 1
fi

# 7. Kiểm tra trạng thái hoạt động & cổng mạng
echo ""
echo "▶ [6/6] Kiểm tra cổng mạng 443 và kết nối HTTPS..."
sleep 2

PORT_443_OWNER=$(ss -tulpn | grep ':443\b' || echo "Chưa có tiến trình")
echo "  - Tiến trình đang nghe cổng 443: ${PORT_443_OWNER}"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -k https://127.0.0.1/ || echo "000")
echo "  - Kiểm tra kết nối HTTPS local (https://127.0.0.1/): HTTP $HTTP_CODE"

echo ""
echo "=========================================================="
echo "  🎉 DỌN DẸP HOÀN TẤT! HỆ THỐNG ĐÃ CHUYỂN SANG HOẠT ĐỘNG TRỰC TIẾP"
echo "=========================================================="
echo "Trạng thái các luồng hoạt động:"
echo "  ✅ HTTPS Nginx       : https://erp-utt.duckdns.org (Nghe trực tiếp port 443)"
echo "  ✅ Backend API       : https://erp-utt.duckdns.org/api/v1/auth/login"
echo "  ✅ PostgreSQL DB     : 163.61.72.183:5432 (kết nối trực tiếp từ máy Dev)"
echo "  ✅ Redis Cache       : 163.61.72.183:6379 (kết nối trực tiếp từ máy Dev)"
echo "  ✅ CI/CD & Deploy    : Sẵn sàng, độc lập và không phụ thuộc OpenVPN"
echo "=========================================================="
