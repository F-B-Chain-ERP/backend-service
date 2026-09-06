# Hướng dẫn Quản trị & Kết nối OpenVPN (2FA OTP + Multi-Dev) — ERP-UTT

Tài liệu này cung cấp hướng dẫn toàn diện về hệ thống VPN nội bộ bảo vệ Database (PostgreSQL 16) và Cache (Redis 7) của hệ thống ERP-UTT.

---

## 1. Tổng quan Kiến trúc Bảo mật Zero Trust

```
[Bên ngoài Internet] ──❌── (Bị UFW Firewall chặn hoàn toàn: Port 5432, 6379, 8080)
                                      │
                                      ▼
[Developer] ──► [OpenVPN GUI] ──► [Mã hóa TLS :1194/UDP] ──► [VPS Server 163.61.72.183]
                   │                                                  │
                   ├─► 1. Tên đăng nhập (Username)                    │
                   ├─► 2. Mật khẩu cá nhân (Password)                 │
                   └─► 3. Mã OTP 6 số (Google Authenticator)          │
                                                                      ▼
                                                       [Cấp IP VPN nội bộ: 10.8.0.x]
                                                                      │
                                          ┌───────────────────────────┴───────────────────────────┐
                                          ▼                                                       ▼
                             [PostgreSQL 16: 10.8.0.1:5432]                           [Redis 7: 10.8.0.1:6379]
```

---

## 2. Dành cho Quản trị viên (Admin Guide)

Thư mục script trên Server: `/opt/ERP-UTT/backend-service/deploy/scripts/`

### A. Khởi tạo OpenVPN Server lần đầu tiên
Chạy một lần duy nhất khi thiết lập server:
```bash
cd /opt/ERP-UTT/backend-service
sudo bash deploy/scripts/06-setup-openvpn.sh
```

### B. Cấp tài khoản cho Developer mới (Onboarding)
Khi có một lập trình viên mới tham gia dự án:
```bash
# Cú pháp: sudo bash deploy/scripts/vpn-add-user.sh <username> [password]
# Ví dụ:
sudo bash deploy/scripts/vpn-add-user.sh dev_nam "MatKhau123@#"
```
**Kết quả hiển thị trên màn hình:**
1. **Mã QR Code**: Cho Dev dùng điện thoại mở Google Authenticator hoặc Authy lên quét.
2. **File cấu hình client**: Được tạo tại `/opt/ERP-UTT/openvpn-clients/dev_nam/dev_nam.ovpn`.
3. Gửi file `dev_nam.ovpn` và mật khẩu cho Developer.

### C. Thu hồi quyền khi Developer nghỉ việc (Offboarding - Revoke 1 chạm)
Khi nhân sự nghỉ việc hoặc đổi dự án, Admin chỉ cần chạy:
```bash
# Cú pháp: sudo bash deploy/scripts/vpn-revoke-user.sh <username>
# Ví dụ:
sudo bash deploy/scripts/vpn-revoke-user.sh dev_nam
```
**Cơ chế bảo mật tức thì:**
- Chứng chỉ SSL của `dev_nam` bị đưa vào danh sách đen **CRL (Certificate Revocation List)**.
- Mật khẩu và secret 2FA bị hủy.
- Kết nối VPN hiện tại (nếu có) bị ngắt ngay lập tức.
- Dù dev đó còn lưu file `.ovpn` trên máy tính cũng không thể nào kết nối được nữa.
- **Quan trọng:** Tất cả các Dev khác (Dev B, Dev C...) vẫn hoạt động bình thường, không phải đổi mật khẩu hay cấu hình lại.

### D. Giám sát & Quản lý
```bash
# Xem danh sách các Dev đang kết nối realtime
cat /var/log/openvpn/openvpn-status.log

# Xem nhật ký hoạt động OpenVPN
tail -f /var/log/openvpn/openvpn.log

# Khởi động lại dịch vụ OpenVPN Server
systemctl restart openvpn-server@server
```

---

## 3. Dành cho Developer (Developer User Guide)

### Bước 1: Cài đặt ứng dụng OpenVPN GUI
- **Windows**: Tải bản cài đặt chính thức tại: https://openvpn.net/community-downloads/ (chọn file Windows 64-bit MSI installer).
- **macOS**: Sử dụng [Tunnelblick](https://tunnelblick.net/) hoặc [OpenVPN Connect](https://openvpn.net/vpn-client/).
- **Linux (Ubuntu)**: `sudo apt install openvpn network-manager-openvpn-gnome`.

### Bước 2: Tải file cấu hình cá nhân về máy tính
Sau khi Admin cấp tài khoản, mở PowerShell / Terminal trên máy của bạn và tải file `.ovpn`:
```powershell
# Thay <username> bằng tên tài khoản của bạn (ví dụ: dev_nam)
scp root@163.61.72.183:/opt/ERP-UTT/openvpn-clients/<username>/<username>.ovpn .
```

### Bước 3: Import file cấu hình vào OpenVPN GUI (Windows)
1. Mở **OpenVPN GUI** (biểu tượng máy tính nhỏ ở thanh Taskbar dưới góc phải màn hình).
2. Nhấp chuột phải vào biểu tượng OpenVPN GUI $\rightarrow$ Chọn **Import file...** $\rightarrow$ Chọn file `<username>.ovpn` vừa tải về.
3. Hoặc copy trực tiếp file `.ovpn` vào thư mục: `C:\Users\<Tên_Máy_Tính>\OpenVPN\config\`.

### Bước 4: Cài đặt Google Authenticator trên điện thoại
1. Mở ứng dụng **Google Authenticator** (hoặc Authy) trên điện thoại iOS / Android.
2. Quét mã QR do Admin cung cấp (hoặc nhập chuỗi Secret Key thủ công).
3. Ứng dụng sẽ hiển thị mã gồm 6 chữ số nhảy liên tục mỗi 30 giây với tên `ERP-UTT (<username>)`.

### Bước 5: Kết nối VPN
1. Nhấp chuột phải vào biểu tượng OpenVPN GUI ở Taskbar $\rightarrow$ Chọn **Connect**.
2. Một hộp thoại popup sẽ xuất hiện yêu cầu thông tin xác thực:
   - **Username**: Nhập tên tài khoản của bạn (ví dụ: `dev_nam`).
   - **Password**: Nhập **[Mật khẩu cá nhân]** ghép liền với **[Mã OTP 6 số hiện tại trên điện thoại]**.
   - *Ví dụ:* Mật khẩu của bạn là `Pass123@#` và mã trên app Google Authenticator đang là `481920` $\rightarrow$ Nhập vào ô Password: `Pass123@#481920`.
3. Bấm **OK**.
4. Biểu tượng OpenVPN chuyển sang **màu xanh lá cây 🟢** $\rightarrow$ Kết nối thành công! Máy của bạn đã được cấp IP nội bộ trong dải VPN (`10.8.0.x`).

### Bước 6: Kết nối Database & Redis từ máy cá nhân
Giờ đây bạn có thể mở công cụ quản trị (DBeaver, DataGrip, pgAdmin) hoặc chạy Backend Spring Boot ở Local:

- **PostgreSQL 16:**
  - Host: `10.8.0.1` (IP Gateway của VPS trong mạng VPN)
  - Port: `5432`
  - Database: `erp_dev`
  - Username: `erp_user`
  - Password: `erp123456@`

- **Redis 7:**
  - Host: `10.8.0.1`
  - Port: `6379`
  - Password: `erp_redis_2026`

- **Backend Local (`application-dev.yaml`):**
  - Cấu hình trỏ tới `10.8.0.1:5432` và `10.8.0.1:6379`.

### Bước 7: Ngắt kết nối khi hoàn thành công việc
- Nhấp chuột phải vào biểu tượng OpenVPN GUI $\rightarrow$ Chọn **Disconnect**.
- Kết nối tới Database trên server sẽ tự động đóng lại, đảm bảo an toàn tuyệt đối.

---

## 4. Xử lý sự cố thường gặp (Troubleshooting)

| Tình huống | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| **Báo lỗi `AUTH_FAILED` khi đăng nhập** | Sai mật khẩu hoặc mã OTP bị hết hạn (quá 30 giây) | Đợi mã OTP nhảy sang số mới trên app điện thoại rồi gõ lại: `[Mật khẩu][Mã 6 số mới]`. |
| **Báo lỗi `TLS Error: TLS handshake failed` hoặc `Certificate revoked`** | Chứng chỉ chưa đúng hoặc tài khoản đã bị Admin thu hồi (Revoke) | Liên hệ Admin kiểm tra trạng thái tài khoản trong danh sách CRL. |
| **OpenVPN kết nối xanh 🟢 nhưng không ping được `10.8.0.1`** | Thiếu route hoặc UFW chưa mở interface `tun+` | Kiểm tra lệnh `sudo ufw status` trên server xem đã có `ALLOW in on tun+` chưa. |
| **Không thể tải file `.ovpn` qua SCP** | Chưa cấu hình SSH Key hoặc sai mật khẩu root | Dùng công cụ FileZilla / WinSCP kết nối SFTP với port 22 để tải file về máy. |
