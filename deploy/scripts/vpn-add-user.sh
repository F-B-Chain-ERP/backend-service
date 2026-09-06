#!/bin/bash
# ==============================================================================
# Script Cấp Tài khoản OpenVPN Riêng cho Developer (2FA OTP + Profile .ovpn)
# Sử dụng trên VPS Ubuntu: sudo bash vpn-add-user.sh <username> [password]
# Ví dụ: sudo bash vpn-add-user.sh dev_nam Pass123@#
# ==============================================================================

set -e

SERVER_IP="163.61.72.183"
SERVER_PORT="1194"
OPENVPN_DIR="/etc/openvpn/server"
EASYRSA_DIR="/etc/openvpn/easy-rsa"
CLIENTS_DIR="/opt/ERP-UTT/openvpn-clients"

USERNAME="$1"
PASSWORD="$2"

if [ -z "$USERNAME" ]; then
    echo "❌ LỖI: Bạn chưa truyền tên tài khoản (username)!"
    echo "Cú pháp: sudo bash vpn-add-user.sh <username> [password]"
    echo "Ví dụ:   sudo bash vpn-add-user.sh dev_nam MyPassword123@"
    exit 1
fi

# Kiểm tra ký tự hợp lệ cho username
if [[ ! "$USERNAME" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo "❌ LỖI: Username chỉ được chứa chữ cái, số, gạch dưới (_) hoặc gạch nối (-)."
    exit 1
fi

# Nếu chưa có password, tự sinh password ngẫu nhiên 12 ký tự
if [ -z "$PASSWORD" ]; then
    PASSWORD=$(openssl rand -base64 9 | tr -dc 'a-zA-Z0-9@#%^&*')
fi

CLIENT_OUT_DIR="$CLIENTS_DIR/$USERNAME"
mkdir -p "$CLIENT_OUT_DIR"

echo "========================================================"
echo " [BƯỚC 1/4] Tạo tài khoản hệ thống cho $USERNAME"
echo "========================================================"

if id "$USERNAME" &>/dev/null; then
    echo "ℹ️ Tài khoản $USERNAME đã tồn tại, tiến hành cập nhật mật khẩu..."
    echo "$USERNAME:$PASSWORD" | chpasswd
    usermod -U "$USERNAME" 2>/dev/null || true
else
    useradd -M -s /usr/sbin/nologin "$USERNAME"
    echo "$USERNAME:$PASSWORD" | chpasswd
    echo "✅ Đã tạo người dùng $USERNAME (Không có quyền SSH, chỉ dùng xác thực VPN)"
fi

echo ""
echo "========================================================"
echo " [BƯỚC 2/4] Thiết lập 2FA Google Authenticator cho $USERNAME"
echo "========================================================"

USER_2FA_DIR="/etc/openvpn/2fa/$USERNAME"
mkdir -p "$USER_2FA_DIR"

# Tạo file secret Google Authenticator (TOTP, rate limit, thời gian 30s)
google-authenticator -t -d -f -r 3 -R 30 -W -s "$USER_2FA_DIR/.google_authenticator" >/dev/null 2>&1

chown -R "$USERNAME:$USERNAME" "$USER_2FA_DIR"
chmod 700 "$USER_2FA_DIR"
chmod 600 "$USER_2FA_DIR/.google_authenticator"

# Lấy mã Secret Key dạng text
SECRET_KEY=$(head -n 1 "$USER_2FA_DIR/.google_authenticator")
OTP_URL="otpauth://totp/ERP-UTT:${USERNAME}?secret=${SECRET_KEY}&issuer=ERP-UTT"

# Xuất file ảnh QR code để gửi cho dev nếu cần
qrencode -o "$CLIENT_OUT_DIR/qrcode.png" "$OTP_URL"

echo ""
echo "========================================================"
echo " [BƯỚC 3/4] Sinh chứng chỉ SSL riêng cho $USERNAME (Easy-RSA)"
echo "========================================================"

cd "$EASYRSA_DIR"
# Xóa chứng chỉ cũ nếu có để sinh mới
if [ -f "pki/issued/$USERNAME.crt" ]; then
    echo "Thu hồi chứng chỉ cũ của $USERNAME trước khi cấp lại..."
    ./easyrsa revoke "$USERNAME" || true
    ./easyrsa gen-crl
    cp pki/crl.pem "$OPENVPN_DIR/crl.pem"
fi

./easyrsa build-client-full "$USERNAME" nopass

echo ""
echo "========================================================"
echo " [BƯỚC 4/4] Đóng gói file cấu hình $USERNAME.ovpn duy nhất"
echo "========================================================"

CA_CERT=$(cat "$OPENVPN_DIR/ca.crt")
CLIENT_CERT=$(cat "pki/issued/$USERNAME.crt")
CLIENT_KEY=$(cat "pki/private/$USERNAME.key")
TLS_AUTH=$(cat "$OPENVPN_DIR/ta.key")

OVPN_FILE="$CLIENT_OUT_DIR/$USERNAME.ovpn"

cat << EOF > "$OVPN_FILE"
# ==============================================================================
# Cấu hình OpenVPN Client ERP-UTT cho Developer: $USERNAME
# Yêu cầu: Xác thực 2 lớp (Password + Google Authenticator OTP)
# ==============================================================================
client
dev tun
proto udp
remote $SERVER_IP $SERVER_PORT
resolv-retry infinite
nobind
persist-key
persist-tun
remote-cert-tls server
cipher AES-256-GCM
auth SHA256
key-direction 1

# Yêu cầu OpenVPN GUI hiển thị popup nhập Username & Password
auth-user-pass

<ca>
$CA_CERT
</ca>

<cert>
$CLIENT_CERT
</cert>

<key>
$CLIENT_KEY
</key>

<tls-auth>
$TLS_AUTH
</tls-auth>
EOF

chmod 600 "$OVPN_FILE"

echo ""
echo "================================================================================"
echo "🎉 CẤP TÀI KHOẢN OPENVPN CHO $USERNAME THÀNH CÔNG!"
echo "================================================================================"
echo ""
echo "📱 QUÉT MÃ QR DƯỚI ĐÂY BẰNG GOOGLE AUTHENTICATOR HOẶC AUTHY TRÊN ĐIỆN THOẠI:"
echo ""
qrencode -t UTF8 "$OTP_URL"
echo ""
echo "Khóa bí mật (Secret Key thủ công nếu không quét được QR): $SECRET_KEY"
echo ""
echo "--------------------------------------------------------------------------------"
echo "🔑 THÔNG TIN ĐĂNG NHẬP CỦA DEVELOPER $USERNAME:"
echo " - Username:    $USERNAME"
echo " - Password:    $PASSWORD"
echo " - Cách đăng nhập trên OpenVPN GUI:"
echo "   + Ô Username: $USERNAME"
echo "   + Ô Password: Nhập [Mật khẩu] liền với [Mã 6 số OTP]"
echo "   + Ví dụ: Nếu OTP trên điện thoại đang là 123456 $\rightarrow$ Nhập: ${PASSWORD}123456"
echo "--------------------------------------------------------------------------------"
echo "📂 FILE CẤU HÌNH ĐÃ TẠO SẴN TRÊN SERVER:"
echo " - File OVPN:   $OVPN_FILE"
echo " - Ảnh QR Code: $CLIENT_OUT_DIR/qrcode.png"
echo ""
echo "📥 LỆNH TẢI VỀ MÁY DEV (Chạy trên máy tính cá nhân qua PowerShell):"
echo " scp root@$SERVER_IP:$OVPN_FILE ."
echo "================================================================================"
