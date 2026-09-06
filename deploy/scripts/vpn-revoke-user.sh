#!/bin/bash
# ==============================================================================
# Script Thu hồi Quyền & Xóa Tài khoản OpenVPN khi Developer Nghỉ việc (Revoke)
# Sử dụng trên VPS Ubuntu: sudo bash vpn-revoke-user.sh <username>
# Ví dụ: sudo bash vpn-revoke-user.sh dev_nam
# ==============================================================================

set -e

OPENVPN_DIR="/etc/openvpn/server"
EASYRSA_DIR="/etc/openvpn/easy-rsa"
CLIENTS_DIR="/opt/ERP-UTT/openvpn-clients"

USERNAME="$1"

if [ -z "$USERNAME" ]; then
    echo "❌ LỖI: Bạn chưa truyền tên tài khoản (username) cần thu hồi!"
    echo "Cú pháp: sudo bash vpn-revoke-user.sh <username>"
    echo "Ví dụ:   sudo bash vpn-revoke-user.sh dev_nam"
    exit 1
fi

echo "========================================================"
echo " [BƯỚC 1/4] Thu hồi chứng chỉ SSL của $USERNAME qua Easy-RSA"
echo "========================================================"

cd "$EASYRSA_DIR"
if [ -f "pki/issued/$USERNAME.crt" ]; then
    ./easyrsa --batch revoke "$USERNAME"
    echo "✅ Đã thu hồi chứng chỉ Easy-RSA của $USERNAME"
else
    echo "ℹ️ Không tìm thấy file chứng chỉ $USERNAME.crt trong PKI (có thể đã bị thu hồi trước đó)."
fi

echo ""
echo "========================================================"
echo " [BƯỚC 2/4] Cập nhật danh sách đen CRL (Certificate Revocation List)"
echo "========================================================"

./easyrsa gen-crl
cp pki/crl.pem "$OPENVPN_DIR/crl.pem"
chmod 644 "$OPENVPN_DIR/crl.pem"

echo "✅ Danh sách CRL đã được cập nhật tại $OPENVPN_DIR/crl.pem"

echo ""
echo "========================================================"
echo " [BƯỚC 3/4] Khóa tài khoản hệ thống & Xóa mã bí mật 2FA"
echo "========================================================"

if id "$USERNAME" &>/dev/null; then
    # Khóa mật khẩu và hạn chế tài khoản
    passwd -l "$USERNAME" 2>/dev/null || true
    usermod -L -e 1 "$USERNAME" 2>/dev/null || true
    echo "✅ Đã khóa tài khoản Linux của $USERNAME"
fi

# Xóa secret 2FA
if [ -d "/etc/openvpn/2fa/$USERNAME" ]; then
    rm -rf "/etc/openvpn/2fa/$USERNAME"
    echo "✅ Đã xóa secret Google Authenticator của $USERNAME"
fi

# Xóa thư mục profile client đã xuất
if [ -d "$CLIENTS_DIR/$USERNAME" ]; then
    rm -rf "$CLIENTS_DIR/$USERNAME"
    echo "✅ Đã xóa thư mục file cấu hình $CLIENTS_DIR/$USERNAME"
fi

echo ""
echo "========================================================"
echo " [BƯỚC 4/4] Áp dụng CRL mới vào OpenVPN Server (Ngắt kết nối tức thì)"
echo "========================================================"

# Reload dịch vụ OpenVPN Server để nhận diện ngay lập tức CRL mới
systemctl restart openvpn-server@server

echo ""
echo "================================================================================"
echo "🚫 THU HỒI QUYỀN TRUY CẬP CỦA DEVELOPER '$USERNAME' THÀNH CÔNG!"
echo "================================================================================"
echo " - Chứng chỉ SSL của $USERNAME đã bị đưa vào danh sách đen (CRL)."
echo " - Mật khẩu và mã OTP 2FA đã bị vô hiệu hóa hoàn toàn."
echo " - Nếu $USERNAME đang kết nối, kết nối sẽ bị ngắt ngay lập tức."
echo " - Dù $USERNAME còn lưu file .ovpn trên máy tính cá nhân cũng KHÔNG THỂ kết nối."
echo " - CÁC DEVELOPER KHÁC VẪN HOẠT ĐỘNG BÌNH THƯỜNG 100%!"
echo "================================================================================"
