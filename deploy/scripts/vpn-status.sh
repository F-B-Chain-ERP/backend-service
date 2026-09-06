#!/bin/bash
# ==============================================================================
# Script Kiểm tra Trạng thái OpenVPN & Danh sách Tài khoản Developer ERP-UTT
# Sử dụng trên VPS: sudo bash vpn-status.sh
# ==============================================================================

STATUS_LOG_1="/run/openvpn-server/status-server.log"
STATUS_LOG_2="/var/log/openvpn/openvpn-status.log"
INDEX_FILE="/etc/openvpn/easy-rsa/pki/index.txt"
FA_DIR="/etc/openvpn/2fa"

echo "================================================================================"
echo "                   📊 TRẠNG THÁI OPENVPN SERVER ERP-UTT                        "
echo "================================================================================"

# 1. Trạng thái dịch vụ OpenVPN Server
if systemctl is-active --quiet openvpn-server@server; then
    echo "🟢 Trạng thái Service: ĐANG CHẠY (Active / Running - Port 443 TCP)"
else
    echo "🔴 Trạng thái Service: ĐÃ DỪNG (Inactive / Failed)"
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "🟢 [1] DEVELOPER ĐANG KẾT NỐI ONLINE (REAL-TIME)"
echo "--------------------------------------------------------------------------------"

STATUS_LOG=""
if [ -f "$STATUS_LOG_1" ]; then
    STATUS_LOG="$STATUS_LOG_1"
elif [ -f "$STATUS_LOG_2" ]; then
    STATUS_LOG="$STATUS_LOG_2"
fi

FOUND_ONLINE=0
if [ -n "$STATUS_LOG" ]; then
    # Parse danh sách client đang online từ OpenVPN Status log
    while IFS=',' read -r col1 col2 col3 col4 col5; do
        if [ "$col1" = "CLIENT_LIST" ]; then
            FOUND_ONLINE=1
            echo " • Developer:     $col2"
            echo "   - IP kết nối:  $col3"
            echo "   - IP VPN nội:  $col4"
            echo "   - Đã nhận/gửi: $col5 bytes"
            echo "   ---"
        elif [ "$col1" != "HEADER" ] && [ "$col1" != "TITLE" ] && [ "$col1" != "GLOBAL_STATS" ] && [ "$col1" != "TIME" ] && [ -n "$col2" ] && [ "$col1" != "ROUTING_TABLE" ] && [[ "$col2" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+: ]]; then
            FOUND_ONLINE=1
            echo " • Developer:     $col1"
            echo "   - IP kết nối:  $col2"
            echo "   - Đã nhận:     $col3 bytes"
            echo "   - Đã gửi:      $col4 bytes"
            echo "   ---"
        fi
    done < "$STATUS_LOG"
fi

if [ "$FOUND_ONLINE" -eq 0 ]; then
    echo "   (Hiện tại không có Developer nào đang kết nối)"
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "📋 [2] TẤT CẢ TÀI KHOẢN DEVELOPER TRÊN HỆ THỐNG"
echo "--------------------------------------------------------------------------------"
printf "%-16s %-14s %-12s %-20s\n" "USERNAME" "TRẠNG THÁI" "2FA (OTP)" "GHI CHÚ"
printf "%-16s %-14s %-12s %-20s\n" "----------------" "--------------" "------------" "--------------------"

if [ -f "$INDEX_FILE" ]; then
    grep -E '^V|^R' "$INDEX_FILE" | while read -r line; do
        FLAG=$(echo "$line" | awk '{print $1}')
        CN=$(echo "$line" | grep -Po 'CN=\K[^/]+' || true)
        
        # Bỏ qua chứng chỉ của server
        if [ "$CN" = "server" ] || [ -z "$CN" ]; then
            continue
        fi

        STATUS="🔴 Đã thu hồi"
        if [ "$FLAG" = "V" ]; then
            STATUS="🟢 Hoạt động"
        fi

        HAS_2FA="❌ Chưa có"
        if [ -f "$FA_DIR/$CN/.google_authenticator" ]; then
            HAS_2FA="✅ Đã cài"
        fi

        printf "%-16s %-14s %-12s %-20s\n" "$CN" "$STATUS" "$HAS_2FA" ""
    done
else
    # Nếu không có index.txt, quét qua thư mục 2fa
    if [ -d "$FA_DIR" ]; then
        for u in "$FA_DIR"/*; do
            if [ -d "$u" ]; then
                uname=$(basename "$u")
                printf "%-16s %-14s %-12s\n" "$uname" "🟢 Hoạt động" "✅ Đã cài"
            fi
        done
    fi
fi

echo ""
echo "================================================================================"
echo "💡 CÁC LỆNH QUẢN LÝ NHANH DÀNH CHO ADMIN:"
echo " • Cấp tài khoản mới: sudo bash deploy/scripts/vpn-add-user.sh <user> [pass] [phút]"
echo " • Tạo lại link tải:  sudo bash deploy/scripts/vpn-share.sh <user> [pass] [phút]"
echo " • Thu hồi quyền:     sudo bash deploy/scripts/vpn-revoke-user.sh <user>"
echo " • Xem trạng thái:    sudo bash deploy/scripts/vpn-status.sh"
echo "================================================================================"
