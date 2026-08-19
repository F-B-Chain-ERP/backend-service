# Hướng dẫn Triển khai & CI/CD cho Hệ thống ERP UTT (Backend & Core)

Server triển khai: **Ubuntu 24.04 LTS** (IP: `163.61.72.183`, Hostname: `vm08181524.bnixvps.io.vn`)

---

## 1. Cấu trúc Dự án trên Server

Trên server (ví dụ đặt tại `/opt/ERP-UTT`):
```
/opt/ERP-UTT/
├── core-model/          (Git repo: https://github.com/F-B-Chain-ERP/core-model.git)
└── backend-service/     (Git repo: https://github.com/F-B-Chain-ERP/backend-service.git)
    ├── deploy/
    │   ├── DEPLOYMENT_GUIDE.md
    │   └── server-setup.sh
    ├── Dockerfile
    └── src/
        ├── main/docker/
        │   ├── app.yml          # Chạy full stack (Backend, Postgres, Redis)
        │   ├── postgresql.yml   # Chạy riêng PostgreSQL
        │   └── redis.yml        # Chạy riêng Redis
        └── main/resources/
            ├── application-dev.yaml
            └── db/changelog/changeset/
                ├── 001-init-schema.sql
                └── 002-init-seed-data.sql
```

---

## 2. Triển khai nhanh trên Server

### Bước 1: SSH vào server
```bash
ssh root@163.61.72.183
```

### Bước 2: Clone các repository
```bash
mkdir -p /opt/ERP-UTT && cd /opt/ERP-UTT

# Clone core-model
git clone https://github.com/F-B-Chain-ERP/core-model.git core-model

# Clone backend-service
git clone https://github.com/F-B-Chain-ERP/backend-service.git backend-service
```

### Bước 3: Chạy script triển khai tự động
```bash
cd /opt/ERP-UTT/backend-service
chmod +x deploy/server-setup.sh
sudo ./deploy/server-setup.sh
```

Script sẽ tự động:
- Cài đặt Docker & Docker Compose Plugin
- Mở Firewall UFW (`22`, `80`, `443`, `8080`, `5432`, `6379`)
- Khởi chạy PostgreSQL, Redis, Backend Service qua `src/main/docker/app.yml` (không cần cấu hình file .env)
- Khởi tạo Database Schema và dữ liệu Admin mặc định

---

## 3. Quy trình Cập nhật & CI/CD khi có Code mới

### A. Khi cập nhật `core-model`:
```bash
cd /opt/ERP-UTT/core-model
git pull origin <branch_name>

cd /opt/ERP-UTT/backend-service
docker compose -f src/main/docker/app.yml up -d --build backend-service
```

### B. Khi cập nhật `backend-service`:
```bash
cd /opt/ERP-UTT/backend-service
git pull origin <branch_name>
docker compose -f src/main/docker/app.yml up -d --build backend-service
```

---

## 4. Hướng dẫn kết nối cho DEV Team

### A. Kết nối Cơ sở dữ liệu (PostgreSQL) từ xa
- **Host:** `163.61.72.183`
- **Port:** `5432`
- **Database:** `erp_dev`
- **Username:** `erp_user`
- **Password:** `erp123456@`
- **JDBC URL:** `jdbc:postgresql://163.61.72.183:5432/erp_dev`

### B. Kết nối Redis từ xa
- **Host:** `163.61.72.183`
- **Port:** `6379`
- **Password:** `erp_redis_2026`

### C. Chạy Local cho Dev:
```bash
# Bật DB & Redis local
docker compose -f src/main/docker/postgresql.yml up -d
docker compose -f src/main/docker/redis.yml up -d

# Chạy Backend (tự động ăn cấu hình application-dev.yaml)
./mvnw spring-boot:run
```

---

## 5. Hướng dẫn kiểm thử cho TESTER

### Thông tin tài khoản Admin:
- **Username:** `admin`
- **Password:** `123456789`
- **Role:** `ADMIN`

### API Đăng nhập:
- **Method:** `POST`
- **URL:** `http://163.61.72.183:8080/api/v1/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body:**
```json
{
  "usernameOrEmail": "admin",
  "password": "123456789"
}
```
- **Response thành công (200 OK):**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

---

## 6. Các lệnh Quản trị thường dùng trên Server

```bash
cd /opt/ERP-UTT/backend-service

# Xem log realtime của backend
docker logs -f erp-backend

# Xem log database postgres
docker logs -f erp-postgres

# Xem trạng thái containers
docker compose -f src/main/docker/app.yml ps

# Khởi động lại dịch vụ
docker compose -f src/main/docker/app.yml restart

# Dừng hệ thống
docker compose -f src/main/docker/app.yml down

# Xóa và tạo mới lại database hoàn toàn (nếu cần reset DB)
docker compose -f src/main/docker/app.yml down -v
docker compose -f src/main/docker/app.yml up -d --build
```
