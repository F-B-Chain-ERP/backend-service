#!/bin/bash
# ==============================================================================
# Script Tạo Link Tải Cấu hình VPN Tự Hủy Sau 5 Phút (Self-Destructing Share Link)
# Bao gồm: Mã QR, Tài khoản, Mật khẩu, Tải file .ovpn và Hướng dẫn kết nối
# Cú pháp: sudo bash vpn-share.sh <username> [password] [expire_minutes]
# Ví dụ:   sudo bash vpn-share.sh dunghd "123456789" 5
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOMAIN="erp-utt.duckdns.org"
CLIENTS_DIR="/opt/ERP-UTT/openvpn-clients"
WEB_SHARES_DIR="/opt/ERP-UTT/vpn-web-shares"
USER_2FA_DIR="/etc/openvpn/2fa"

USERNAME="$1"
PASSWORD="$2"
EXPIRE_MINUTES="${3:-15}"  # Mặc định tự hủy sau 15 phút

if [ -z "$USERNAME" ]; then
    echo "❌ LỖI: Bạn chưa truyền tên tài khoản (username)!"
    echo "Cú pháp: sudo bash vpn-share.sh <username> [password] [expire_minutes]"
    echo "Ví dụ:   sudo bash vpn-share.sh dunghd 123456789 15"
    exit 1
fi

USER_OVPN="$CLIENTS_DIR/$USERNAME/$USERNAME.ovpn"
USER_QR="$CLIENTS_DIR/$USERNAME/qrcode.png"
USER_SECRET_FILE="$USER_2FA_DIR/$USERNAME/.google_authenticator"

if [ ! -f "$USER_OVPN" ]; then
    if [ -f "$SCRIPT_DIR/vpn-add-user.sh" ]; then
        echo "ℹ️ Tài khoản '$USERNAME' chưa tồn tại cấu hình VPN (thiếu $USER_OVPN)."
        echo "🔄 Đang tự động gọi 'vpn-add-user.sh' để khởi tạo tài khoản, sinh chứng chỉ và tạo link bàn giao..."
        echo ""
        exec bash "$SCRIPT_DIR/vpn-add-user.sh" "$USERNAME" "$PASSWORD" "$EXPIRE_MINUTES"
    else
        echo "❌ LỖI: Không tìm thấy file cấu hình $USER_OVPN!"
        echo "Vui lòng kiểm tra lại tài khoản hoặc tạo mới bằng: sudo bash deploy/scripts/vpn-add-user.sh $USERNAME"
        exit 1
    fi
fi

SECRET_KEY="Không xác định"
if [ -f "$USER_SECRET_FILE" ]; then
    SECRET_KEY=$(head -n 1 "$USER_SECRET_FILE")
fi

if [ -z "$PASSWORD" ]; then
    PASSWORD="[Mật khẩu đã được cấp riêng cho bạn]"
fi

# Tạo mã token ngẫu nhiên 24 ký tự bảo mật
TOKEN=$(openssl rand -hex 12)
SHARE_DIR="$WEB_SHARES_DIR/$TOKEN"

mkdir -p "$SHARE_DIR"

# Copy file cấu hình và mã QR vào thư mục web share
cp "$USER_OVPN" "$SHARE_DIR/"
if [ -f "$USER_QR" ]; then
    cp "$USER_QR" "$SHARE_DIR/"
fi

# Tự sinh mã QR nếu chưa có
OTP_URL="otpauth://totp/ERP-UTT:${USERNAME}?secret=${SECRET_KEY}&issuer=ERP-UTT"
if [ ! -f "$SHARE_DIR/qrcode.png" ] && [ -n "$SECRET_KEY" ] && [ "$SECRET_KEY" != "Không xác định" ]; then
    qrencode -o "$SHARE_DIR/qrcode.png" "$OTP_URL" 2>/dev/null || true
fi

# Chuyển QR Code sang Base64 Data URL nhúng trực tiếp vào HTML (tránh lỗi 404 và hiển thị tức thì 100%)
QR_SRC="qrcode.png"
if [ -f "$SHARE_DIR/qrcode.png" ]; then
    QR_B64=$(base64 -w 0 "$SHARE_DIR/qrcode.png" 2>/dev/null || base64 "$SHARE_DIR/qrcode.png" | tr -d '\r\n')
    if [ -n "$QR_B64" ]; then
        QR_SRC="data:image/png;base64,$QR_B64"
    fi
fi

# Tính thời gian hết hạn dạng Unix timestamp cho JavaScript đếm ngược
EXPIRE_SECONDS=$((EXPIRE_MINUTES * 60))
TIMER_INIT_STR=$(printf "%02d:00" "$EXPIRE_MINUTES")

# Tạo trang HTML bàn giao hiện đại, có đồng hồ đếm ngược tự hủy
cat << EOF > "$SHARE_DIR/index.html"
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cấu hình Kết nối OpenVPN ERP-UTT — $USERNAME</title>
    <style>
        :root {
            --bg-color: #0b0f19;
            --card-bg: #151d30;
            --primary: #3b82f6;
            --primary-hover: #2563eb;
            --danger: #ef4444;
            --danger-bg: rgba(239, 68, 68, 0.15);
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #27354f;
            --accent-green: #10b981;
            --accent-amber: #f59e0b;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
        body {
            background-color: var(--bg-color);
            color: var(--text-main);
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            padding: 20px;
        }
        .container {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 20px;
            max-width: 620px;
            width: 100%;
            padding: 32px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
            position: relative;
        }
        /* Thanh đếm ngược tự hủy */
        .timer-banner {
            background: linear-gradient(135deg, rgba(239, 68, 68, 0.2), rgba(245, 158, 11, 0.2));
            border: 1px solid rgba(239, 68, 68, 0.4);
            border-radius: 12px;
            padding: 12px 16px;
            margin-bottom: 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .timer-text {
            font-size: 0.85rem;
            color: #fca5a5;
            font-weight: 500;
        }
        .timer-badge {
            background-color: var(--danger);
            color: white;
            font-weight: 700;
            font-size: 1.1rem;
            font-family: monospace;
            padding: 4px 12px;
            border-radius: 8px;
            letter-spacing: 1px;
            box-shadow: 0 2px 4px rgba(239, 68, 68, 0.4);
        }
        .header {
            text-align: center;
            margin-bottom: 24px;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 16px;
        }
        .header h1 {
            font-size: 1.5rem;
            font-weight: 700;
            color: var(--text-main);
        }
        .badge {
            display: inline-block;
            background-color: rgba(59, 130, 246, 0.2);
            color: #60a5fa;
            font-size: 0.85rem;
            padding: 4px 12px;
            border-radius: 9999px;
            margin-top: 8px;
            font-weight: 600;
        }
        .step {
            margin-bottom: 24px;
        }
        .step-title {
            font-size: 1rem;
            font-weight: 600;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 10px;
            color: #e2e8f0;
        }
        .step-number {
            background-color: var(--primary);
            color: white;
            width: 26px;
            height: 26px;
            border-radius: 50%;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 0.85rem;
            font-weight: bold;
        }
        /* Bảng thông tin tài khoản */
        .cred-grid {
            background: rgba(11, 15, 25, 0.7);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 16px;
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px;
        }
        .cred-item label {
            display: block;
            font-size: 0.75rem;
            color: var(--text-muted);
            margin-bottom: 4px;
            text-transform: uppercase;
            font-weight: 600;
        }
        .cred-value {
            font-family: monospace;
            font-size: 1rem;
            color: #38bdf8;
            background: #0b0f19;
            padding: 8px 12px;
            border-radius: 8px;
            border: 1px solid #1e293b;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .copy-btn {
            background: transparent;
            border: none;
            color: #64748b;
            cursor: pointer;
            font-size: 0.85rem;
            padding: 2px 6px;
            border-radius: 4px;
        }
        .copy-btn:hover {
            color: #38bdf8;
            background: rgba(56, 189, 248, 0.1);
        }
        .qr-container {
            text-align: center;
            background: rgba(11, 15, 25, 0.7);
            border: 1px solid var(--border-color);
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 16px;
        }
        .qr-box {
            background: white;
            padding: 16px;
            border-radius: 12px;
            display: inline-block;
            margin: 0 auto 12px;
        }
        .qr-box img {
            width: 190px;
            height: 190px;
            display: block;
        }
        .secret-box {
            font-family: monospace;
            background: #0b0f19;
            padding: 8px 12px;
            border-radius: 8px;
            border: 1px solid #1e293b;
            color: #38bdf8;
            word-break: break-all;
            font-size: 0.9rem;
            margin-top: 8px;
        }
        .btn-download {
            display: block;
            width: 100%;
            background-color: var(--primary);
            color: white;
            text-align: center;
            padding: 14px 20px;
            border-radius: 12px;
            text-decoration: none;
            font-weight: 700;
            font-size: 1rem;
            box-shadow: 0 4px 14px rgba(59, 130, 246, 0.4);
            transition: all 0.2s;
        }
        .btn-download:hover {
            background-color: var(--primary-hover);
            transform: translateY(-1px);
        }
        .guide-box {
            background: rgba(11, 15, 25, 0.7);
            border: 1px solid var(--border-color);
            padding: 18px;
            border-radius: 12px;
            font-size: 0.9rem;
            color: var(--text-muted);
            line-height: 1.6;
        }
        .guide-box strong { color: var(--text-main); }
        .highlight { color: #fbbf24; font-weight: 600; }
        .footer {
            text-align: center;
            margin-top: 20px;
            font-size: 0.75rem;
            color: #64748b;
        }
        /* Màn hình sau khi tự hủy */
        #expired-view {
            display: none;
            text-align: center;
            padding: 40px 20px;
        }
        #expired-view h2 {
            color: var(--danger);
            font-size: 1.5rem;
            margin-bottom: 12px;
        }
        #expired-view p {
            color: var(--text-muted);
            line-height: 1.6;
        }
    </style>
</head>
<body>
    <div class="container">
        <!-- Banner đếm ngược tự hủy -->
        <div class="timer-banner" id="timer-bar">
            <div class="timer-text">
                ⚠️ <strong>Bảo mật cao:</strong> Trang cài đặt sẽ tự hủy sau:
            </div>
            <div class="timer-badge" id="countdown">$TIMER_INIT_STR</div>
        </div>

        <div id="content-view">
            <div class="header">
                <h1>🔒 Cấu hình Kết nối OpenVPN ERP-UTT</h1>
                <div class="badge">Người dùng: $USERNAME</div>
            </div>

            <!-- Bước 1: Thông tin tài khoản -->
            <div class="step">
                <div class="step-title">
                    <span class="step-number">1</span>
                    <span>Tài khoản xác thực cá nhân</span>
                </div>
                <div class="cred-grid">
                    <div class="cred-item">
                        <label>Tên đăng nhập (Username)</label>
                        <div class="cred-value">
                            <span id="u-val">$USERNAME</span>
                            <button class="copy-btn" onclick="copyText('u-val')">📋 Copy</button>
                        </div>
                    </div>
                    <div class="cred-item">
                        <label>Mật khẩu (Password)</label>
                        <div class="cred-value">
                            <span id="p-val">$PASSWORD</span>
                            <button class="copy-btn" onclick="copyText('p-val')">📋 Copy</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Bước 2: Quét QR Code 2FA -->
            <div class="step">
                <div class="step-title">
                    <span class="step-number">2</span>
                    <span>Quét mã xác thực 2 lớp (Google Authenticator)</span>
                </div>
                <div class="qr-container">
                    <div class="qr-box">
                        <img src="$QR_SRC" alt="Google Authenticator QR Code">
                    </div>
                    <div style="font-size: 0.85rem; color: var(--text-muted);">Mở Google Authenticator hoặc Authy trên điện thoại và quét mã trên.</div>
                    <div style="font-size: 0.8rem; color: #64748b; margin-top: 10px;">Khóa bí mật thủ công (nếu camera không quét được):</div>
                    <div class="secret-box">$SECRET_KEY</div>
                </div>
            </div>

            <!-- Bước 3: Tải file cấu hình .ovpn -->
            <div class="step">
                <div class="step-title">
                    <span class="step-number">3</span>
                    <span>Tải file cấu hình kết nối VPN</span>
                </div>
                <a href="$USERNAME.ovpn" download class="btn-download">
                    📥 Tải file cấu hình $USERNAME.ovpn
                </a>
            </div>

            <!-- Bước 4: Hướng dẫn đăng nhập -->
            <div class="step">
                <div class="step-title">
                    <span class="step-number">4</span>
                    <span>Hướng dẫn kết nối với OpenVPN GUI</span>
                </div>
                <div class="guide-box">
                    1. Tải và cài đặt ứng dụng <a href="https://openvpn.net/community-downloads/" target="_blank" style="color: #60a5fa; font-weight: 600;">OpenVPN GUI cho Windows</a>.<br>
                    2. Nhấp chuột phải icon OpenVPN GUI ở khay Taskbar (góc phải dưới) $\rightarrow$ Chọn <strong>Import file...</strong> $\rightarrow$ Chọn file <strong>$USERNAME.ovpn</strong> vừa tải.<br>
                    3. Bấm <strong>Connect</strong> và đăng nhập với 2 hộp thoại riêng biệt:<br>
                    &bull; <strong>Hộp thoại 1:</strong> Nhập Username: <span class="highlight">$USERNAME</span> và Password: <span class="highlight">$PASSWORD</span> (chỉ nhập mật khẩu).<br>
                    &bull; <strong>Hộp thoại 2 (hiện riêng biệt):</strong> Nhập mã 6 số OTP từ ứng dụng Google Authenticator trên điện thoại.<br><br>
                    🎉 <strong>Địa chỉ kết nối Database & Cache nội bộ (sau khi kết nối VPN):</strong><br>
                    &bull; <strong>PostgreSQL 16:</strong> Host: <code>10.8.0.1</code> | Port: <code>5432</code> | DB: <code>erp_dev</code><br>
                    &bull; <strong>Redis 7:</strong> Host: <code>10.8.0.1</code> | Port: <code>6379</code>
                </div>
            </div>

            <div class="footer">
                Trang này chứa thông tin bảo mật và sẽ tự động xóa sạch dữ liệu sau thời gian đếm ngược.
            </div>
        </div>

        <!-- Màn hình hiển thị sau khi hết hạn 5 phút -->
        <div id="expired-view">
            <h2>⏳ Liên kết đã hết hạn (Expired)</h2>
            <p>Vì lý do an toàn bảo mật, thông tin cài đặt và tệp cấu hình của liên kết này đã được hệ thống tự động xóa sạch.</p>
            <p style="margin-top: 12px; font-size: 0.85rem; color: #64748b;">Nếu bạn chưa kịp cấu hình hoặc cần cấp lại, vui lòng liên hệ Admin.</p>
        </div>
    </div>

    <script>
        // Thiết lập bộ đếm ngược 5 phút
        let timeLeft = $EXPIRE_SECONDS;
        const countdownEl = document.getElementById('countdown');
        const contentView = document.getElementById('content-view');
        const timerBar = document.getElementById('timer-bar');
        const expiredView = document.getElementById('expired-view');

        const timer = setInterval(() => {
            timeLeft--;
            if (timeLeft <= 0) {
                clearInterval(timer);
                timerBar.style.display = 'none';
                contentView.style.display = 'none';
                expiredView.style.display = 'block';
            } else {
                const minutes = Math.floor(timeLeft / 60);
                const seconds = timeLeft % 60;
                countdownEl.textContent = 
                    (minutes < 10 ? '0' : '') + minutes + ':' + 
                    (seconds < 10 ? '0' : '') + seconds;
            }
        }, 1000);

        function copyText(id) {
            const text = document.getElementById(id).textContent;
            navigator.clipboard.writeText(text).then(() => {
                alert('Đã sao chép: ' + text);
            });
        }
    </script>
</body>
</html>
EOF

# Phân quyền cho Nginx đọc được
chmod 755 /opt/ERP-UTT 2>/dev/null || true
chmod -R 755 "$WEB_SHARES_DIR"
chown -R www-data:www-data "$WEB_SHARES_DIR" 2>/dev/null || true

SHARE_URL="https://$DOMAIN/vpn-setup/$TOKEN/"

# KÍCH HOẠT TIẾN TRÌNH TỰ ĐỘNG XÓA THƯ MỤC TRÊN SERVER SAU ĐÚNG $EXPIRE_MINUTES PHÚT
nohup bash -c "sleep $EXPIRE_SECONDS && rm -rf '$SHARE_DIR'" >/dev/null 2>&1 &

echo ""
echo "================================================================================"
echo "🎉 TẠO LINK BÀN GIAO CHO DEVELOPER '$USERNAME' THÀNH CÔNG!"
echo "================================================================================"
echo ""
echo "👉 HÃY GỬI NGAY ĐƯỜNG LINK NÀY CHO DEVELOPER ($USERNAME):"
echo ""
echo "   🔗 Domain HTTPS:    $SHARE_URL"
echo "   🔗 IP VPS dự phòng: https://163.61.72.183/vpn-setup/$TOKEN/"
echo ""
echo "--------------------------------------------------------------------------------"
echo "⏳ CƠ CHẾ BẢO MẬT TỰ HỦY (SELF-DESTRUCT):"
echo " - Liên kết này chỉ tồn tại trong vòng $EXPIRE_MINUTES PHÚT."
echo " - Trên trang có đồng hồ đếm ngược thời gian thực."
echo " - Sau $EXPIRE_MINUTES phút, toàn bộ thư mục và tệp trên server sẽ TỰ ĐỘNG BỊ XÓA VĨNH VIỄN."
echo " - Trình duyệt sau khi hết giờ sẽ tự chuyển sang màn hình báo 'Expired'."
echo "================================================================================"
