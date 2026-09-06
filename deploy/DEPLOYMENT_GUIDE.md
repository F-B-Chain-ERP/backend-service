# Hướng dẫn Triển khai & Vận hành ERP UTT (Local-First → Server)

Server triển khai: **Ubuntu 24.04 LTS** (IP: `163.61.72.183`, Hostname: `vm08181524.bnixvps.io.vn`)

---

## 1. Cấu trúc Triển khai trên Server

Thư mục trên Server: `/opt/ERP-UTT/backend-service`
```
/opt/ERP-UTT/backend-service/
├── deploy/
│   ├── DEPLOYMENT_GUIDE.md
│   ├── OPENVPN_MANAGEMENT_GUIDE.md   # Hướng dẫn OpenVPN 2FA OTP cho Admin & Dev
│   ├── server-setup.sh               # Cài đặt server & khởi động infra
│   └── scripts/
│       ├── 01-server-infra.sh        # Chạy trên server: Khởi động DB & Redis
│       ├── 02-build-push.sh / .ps1   # Chạy trên local: Build & chuyển image lên server
│       ├── 03-deploy-app.sh          # Chạy trên server: Khởi chạy backend container
│       ├── 05-harden-firewall.sh     # Thiết lập tường lửa UFW (SSH, HTTP, HTTPS, DB, Redis)
│       ├── cleanup-vpn-server.sh     # Dọn dẹp OpenVPN trên VPS -> chuyển Nginx trực tiếp cổng 443
│       └── [Tùy chọn tương lai]
│           ├── 06-setup-openvpn.sh   # (Dự phòng) Dựng OpenVPN Server 2FA OTP + CRL
│           ├── vpn-add-user.sh       # (Dự phòng) Cấp tài khoản VPN + OTP cho Dev
│           ├── vpn-share.sh          # (Dự phòng) Tạo link tải cấu hình VPN tự hủy
│           ├── vpn-revoke-user.sh    # (Dự phòng) Thu hồi quyền khi Dev nghỉ việc
│           └── vpn-status.sh         # (Dự phòng) Kiểm tra trạng thái OpenVPN
└── src/
    └── main/docker/
        ├── infra.yml                 # Compose chỉ chạy PostgreSQL + Redis
        ├── app.yml                   # Compose chạy Backend (127.0.0.1:8080 nội bộ)
        ├── postgresql.yml            # Service PostgreSQL 16
        └── redis.yml                 # Service Redis 7
```

---

## 2. Quy trình Triển khai 7 bước (Local-First)

```
[SERVER: 163.61.72.183]                     [LOCAL DEV]
────────────────────────────────────────    ──────────────────────────────────────
Bước 1: Khởi động Hạ tầng (DB + Redis)
  sudo bash deploy/server-setup.sh
  (hoặc bash deploy/scripts/01-server-infra.sh)

                                            Bước 2: Kết nối thử DB & Redis từ Local
                                              Test port 5432 & 6379

                                            Bước 3: Chạy SQL Migration thủ công
                                              Chạy changeset 001, 002,... vào DB

                                            Bước 4: Chạy Spring Boot Local
                                              ./mvnw spring-boot:run
                                              (kết nối thẳng vào DB & Redis server)

                                            Bước 5: Kiểm thử API Local
                                              Test login POST /api/v1/auth/login

                                            Bước 6: Build Docker Image từ Local
                                              .\backend-service\deploy\scripts\02-build-push.ps1
                                              (Build & load image lên server qua SSH)

Bước 7: Khởi chạy Backend trên Server
  sudo bash deploy/scripts/03-deploy-app.sh
```

---

### Chi tiết từng bước thực hiện:

### Bước 1: Khởi động Hạ tầng trên Server
SSH vào server và chạy:
```bash
ssh root@163.61.72.183

# Di chuyển vào thư mục backend-service
cd /opt/ERP-UTT/backend-service

# Khởi chạy PostgreSQL + Redis (hoặc dùng server-setup.sh nếu là server mới)
chmod +x deploy/scripts/*.sh
sudo bash deploy/scripts/01-server-infra.sh
```

### Bước 2: Kiểm tra kết nối từ Local vào Database & Redis
> 💡 **Kết nối trực tiếp nhanh gọn:** Các port 5432 (PostgreSQL) và 6379 (Redis) được mở trực tiếp trên Firewall Server (hoặc có thể kết nối bảo mật qua SSH Tunnel: `ssh -N -L 5432:localhost:5432 root@163.61.72.183`).

Từ máy local dev, mở DBeaver / DataGrip / pgAdmin hoặc Redis GUI:
- **PostgreSQL:**
  - Host: `163.61.72.183` | Port: `5432` | DB: `erp_dev` | User: `erp_user` | Pass: `erp123456@`
- **Redis:**
  - Host: `163.61.72.183` | Port: `6379` | Pass: `erp_redis_2026`

### Bước 3: Chạy SQL Migration thủ công (Liquibase changesets)
Dev mở các file SQL trong `src/main/resources/db/changelog/changeset/` và chạy lần lượt vào DB trên Server:
1. `001-init-schema.sql`: Khởi tạo toàn bộ schema (baseline, hợp nhất từ các changeset cũ)
2. `003-branch.sql` … `011-platform.sql`: Tạo schema từng module (branch, inv, proc, menu, store, pos, fin, platform)
3. `012-permission-seed.sql`: Seed 199 permission + bootstrap tối thiểu (scope `ALL_SYSTEM`, role `ADMIN`, tài khoản `admin` / `123456789`) và gán full quyền cho `ADMIN`

> **Lưu ý:** Dữ liệu init/test cũ (role USER, test account `manager01`/`staff01`/khách hàng test…) đã được xóa.
> Nếu DB cũ đã chạy migration, hãy `TRUNCATE TABLE databasechangelog;` trước khi chạy lại để tránh trùng lịch sử.

### Bước 4: Chạy Backend Local kết nối Server DB
Trong `application-dev.yaml`, cấu hình mặc định đã trỏ thẳng tới IP Server `163.61.72.183`. Chạy backend:
```bash
cd backend-service
./mvnw spring-boot:run
```

### Bước 5: Kiểm thử API Local
Gửi request kiểm tra login:
- **Method:** `POST`
- **URL:** `http://localhost:8080/api/v1/auth/login`
- **Body:**
```json
{
  "usernameOrEmail": "admin",
  "password": "123456789"
}
```
Xác nhận trả về `200 OK` kèm access token.

### Bước 6: Build & Load Docker Image lên Server (từ Local)

**Trên Windows PowerShell:**
```powershell
# Từ thư mục gốc ERP-UTT
.\backend-service\deploy\scripts\02-build-push.ps1 -Tag "latest"
```

**Trên Linux / macOS / Git Bash:**
```bash
# Từ thư mục gốc ERP-UTT
bash backend-service/deploy/scripts/02-build-push.sh latest 163.61.72.183
```

Script sẽ tự động build image đa tầng và transfer sang server qua SSH.

### Bước 7: Khởi chạy Backend trên Server
SSH vào server và chạy:
```bash
ssh root@163.61.72.183
cd /opt/ERP-UTT/backend-service
sudo bash deploy/scripts/03-deploy-app.sh latest
```

Kiểm tra API trên server (qua Reverse Proxy HTTPS):
- **URL HTTPS:** `https://erp-utt.duckdns.org/api/v1/auth/login`
- **Nội bộ VPS:** `http://127.0.0.1:8080/actuator/health`

---

## 3. Quản trị Tường lửa & Dọn dẹp OpenVPN trên Server

### A. Thiết lập tường lửa UFW tiêu chuẩn (SSH, HTTP, HTTPS, DB, Redis):
```bash
cd /opt/ERP-UTT/backend-service
sudo bash deploy/scripts/05-harden-firewall.sh
```

### B. Dọn dẹp OpenVPN trên Server & Chuyển Nginx sang cổng 443 trực tiếp:
```bash
cd /opt/ERP-UTT/backend-service
sudo bash deploy/scripts/cleanup-vpn-server.sh
```

> 📌 **Ghi chú về OpenVPN:**
> Hệ thống hiện tại vận hành trực tiếp qua HTTPS (Nginx 443) và Direct Port DB/Redis để quy trình CI/CD và triển khai đơn giản, nhanh nhất.
> Nếu trong tương lai doanh nghiệp muốn siết chặt bảo mật thêm kênh OpenVPN 2FA OTP, toàn bộ script (`06-setup-openvpn.sh`, `vpn-add-user.sh`,...) và tài liệu hướng dẫn [OPENVPN_MANAGEMENT_GUIDE.md](file:///c:/ERP-UTT/backend-service/deploy/OPENVPN_MANAGEMENT_GUIDE.md) vẫn được lưu trữ đầy đủ trong repo để kích hoạt lại bất kỳ lúc nào.

---

## 4. Các lệnh Quản trị thường dùng trên Server

```bash
cd /opt/ERP-UTT/backend-service

# Xem logs backend realtime
docker logs -f erp-backend

# Xem logs database postgres
docker logs -f erp-postgres

# Xem logs redis
docker logs -f erp-redis

# Xem trạng thái tất cả containers
docker compose -f src/main/docker/app.yml ps

# Khởi động lại riêng backend
docker compose -f src/main/docker/app.yml restart backend-service

# Khởi động lại riêng hạ tầng (Postgres / Redis)
docker compose -f src/main/docker/infra.yml restart

# Dừng toàn bộ hệ thống
docker compose -f src/main/docker/app.yml down
```
