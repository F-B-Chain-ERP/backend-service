#!/bin/bash
# ==============================================================================
# Script Cài đặt & Khởi tạo OpenVPN Server với Xác thực 2FA OTP + CRL cho ERP-UTT
# Chạy trên Server VPS Ubuntu 24.04: sudo bash 06-setup-openvpn.sh
# ==============================================================================

set -e

SERVER_IP="163.61.72.183"
OPENVPN_DIR="/etc/openvpn/server"
EASYRSA_DIR="/etc/openvpn/easy-rsa"

echo "========================================================"
echo " [BƯỚC 1/6] Cài đặt OpenVPN, Easy-RSA, Google Authenticator, QRencode"
echo "========================================================"
apt update
apt install -y openvpn easy-rsa libpam-google-authenticator qrencode iptables

# Tạo các thư mục cần thiết
mkdir -p "$OPENVPN_DIR"
mkdir -p /var/log/openvpn
mkdir -p /opt/ERP-UTT/openvpn-clients

echo ""
echo "========================================================"
echo " [BƯỚC 2/6] Khởi tạo PKI (Public Key Infrastructure) & CA"
echo "========================================================"
rm -rf "$EASYRSA_DIR"
make-cadir "$EASYRSA_DIR"
cd "$EASYRSA_DIR"

# Cấu hình vars cho Easy-RSA không tương tác (sử dụng chuẩn RSA 2048bit)
cat << 'EOF' > vars
set_var EASYRSA_ALGO "rsa"
set_var EASYRSA_KEY_SIZE 2048
set_var EASYRSA_DIGEST "sha256"
set_var EASYRSA_BATCH "1"
EOF

# Khởi tạo PKI và xây dựng CA
./easyrsa init-pki
EASYRSA_REQ_CN="ERP-UTT-CA" ./easyrsa build-ca nopass

# Sinh Certificate & Private Key cho OpenVPN Server
./easyrsa build-server-full server nopass

# Sinh Diffie-Hellman parameters
./easyrsa gen-dh

# Khởi tạo danh sách thu hồi chứng chỉ (CRL - Certificate Revocation List)
./easyrsa gen-crl

# Tạo TLS Auth Key (HMAC firewall chống DoS port scanning)
openvpn --genkey secret "$OPENVPN_DIR/ta.key"

# Sao chép các chứng chỉ và khóa vào thư mục OpenVPN Server
cp pki/ca.crt "$OPENVPN_DIR/"
cp pki/issued/server.crt "$OPENVPN_DIR/"
cp pki/private/server.key "$OPENVPN_DIR/"
cp pki/dh.pem "$OPENVPN_DIR/"
cp pki/crl.pem "$OPENVPN_DIR/"
chmod 644 "$OPENVPN_DIR/crl.pem"

echo ""
echo "========================================================"
echo " [BƯỚC 3/6] Cấu hình PAM cho xác thực 2FA (Password + Google Authenticator)"
echo "========================================================"

mkdir -p /etc/openvpn/2fa
chmod 755 /etc/openvpn/2fa

cat << 'EOF' > /etc/pam.d/openvpn
# Cấu hình xác thực 2 lớp riêng biệt: Mật khẩu và OTP nhập ở 2 ô riêng
auth required pam_unix.so
auth required pam_google_authenticator.so secret=/etc/openvpn/2fa/${USER}/.google_authenticator authtok_prompt=pin
account required pam_unix.so
EOF

echo ""
echo "========================================================"
echo " [BƯỚC 4/6] Tạo file cấu hình OpenVPN Server (/etc/openvpn/server/server.conf)"
echo "========================================================"

cat << EOF > "$OPENVPN_DIR/server.conf"
# Cổng & Giao thức
port 1194
proto udp
dev tun

# Chứng chỉ & Khóa
ca ca.crt
cert server.crt
key server.key
dh dh.pem
tls-auth ta.key 0

# Dải mạng VPN nội bộ
server 10.8.0.0 255.255.255.0

# Điều hướng route mạng nội bộ tới client
push "route 10.8.0.0 255.255.255.0"

# Danh sách chứng chỉ đã bị thu hồi (Khi Dev nghỉ việc)
crl-verify crl.pem

# Xác thực người dùng qua PAM (Mật khẩu và OTP tách riêng biệt)
plugin /usr/lib/x86_64-linux-gnu/openvpn/plugins/openvpn-plugin-auth-pam.so "openvpn login USERNAME password PASSWORD pin OTP"
verify-client-cert require
username-as-common-name

# Bảo mật & Mã hóa
cipher AES-256-GCM
auth SHA256
keepalive 10 120
persist-key
persist-tun

# Logging
status /var/log/openvpn/openvpn-status.log
log-append /var/log/openvpn/openvpn.log
verb 3
mute 20
EOF

echo ""
echo "========================================================"
echo " [BƯỚC 5/6] Kích hoạt IP Forwarding & NAT Masquerade"
echo "========================================================"

# Bật IP Forwarding vĩnh viễn
echo "net.ipv4.ip_forward = 1" > /etc/sysctl.d/99-openvpn.conf
sysctl -p /etc/sysctl.d/99-openvpn.conf

# Cấu hình NAT trong UFW before.rules
DEFAULT_INTERFACE=$(ip -4 route ls | grep default | grep -Po '(?<=dev )(\S+)' | head -1)

UFW_BEFORE="/etc/ufw/before.rules"
if ! grep -q "OPENVPN NAT" "$UFW_BEFORE"; then
    sed -i "1i # OPENVPN NAT RULES\n*nat\n:POSTROUTING ACCEPT [0:0]\n-A POSTROUTING -s 10.8.0.0/24 -o $DEFAULT_INTERFACE -j MASQUERADE\nCOMMIT\n" "$UFW_BEFORE"
    echo "Đã thêm NAT Masquerade cho $DEFAULT_INTERFACE vào $UFW_BEFORE"
fi

echo ""
echo "========================================================"
echo " [BƯỚC 6/6] Khởi động Dịch vụ OpenVPN Server"
echo "========================================================"

systemctl daemon-reload
systemctl enable openvpn-server@server
systemctl restart openvpn-server@server

# Kiểm tra trạng thái
systemctl is-active --quiet openvpn-server@server && echo "✅ OpenVPN Server đang chạy thành công!" || echo "⚠️ Kiểm tra logs: journalctl -u openvpn-server@server -e"

echo ""
echo "========================================================"
echo "✅ HOÀN TẤT CÀI ĐẶT OPENVPN SERVER!"
echo " Tiếp theo, để cấp tài khoản cho một Dev mới:"
echo " sudo bash /opt/ERP-UTT/backend-service/deploy/scripts/vpn-add-user.sh <username>"
echo "========================================================"
