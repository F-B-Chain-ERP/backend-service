# Hướng dẫn Triển khai & Vận hành ERP UTT (CI/CD + Server)

Server triển khai: **Ubuntu 24.04 LTS** (IP: `163.61.72.183`, Hostname: `vm08181524.bnixvps.io.vn`)

---

## 1. Cấu trúc Triển khai trên Server
 
Thư mục trên Server: `~/backend-service` (hoặc `/home/devops/backend-service`)
```
~/backend-service/
├── .github/
│   └── workflows/
│       └── ci-cd.yml                 # GitHub Actions Pipeline (Build -> GHCR -> SSH Deploy)
├── deploy/
│   ├── DEPLOYMENT_GUIDE.md           # Tài liệu hướng dẫn này
│   ├── server-setup.sh               # Cài đặt server & khởi động infra ban đầu
│   └── scripts/
│       ├── 01-server-infra.sh        # Khởi chạy DB (Postgres 16) & Cache (Redis 7)
│       ├── 02-build-push.sh / .ps1   # (Dự phòng) Build & load image thủ công từ local
│       ├── 03-deploy-app.sh          # (Dự phòng) Khởi chạy backend cơ bản
│       ├── deploy.sh                 # [MỚI] Deploy tự động với Health Check & Rollback
│       └── rollback.sh               # [MỚI] Rollback 1 chạm về phiên bản stable trước
└── src/
    └── main/docker/
        ├── infra.yml                 # Compose chỉ chạy PostgreSQL + Redis
        ├── app.yml                   # Compose chạy Backend (kết nối DB/Redis)
        ├── postgresql.yml            # Service PostgreSQL 16
        └── redis.yml                 # Service Redis 7
```

---

## 2. Quy trình CI/CD Tự động với GitHub Actions (Chuẩn)

Pipeline hoạt động khi có code được merge vào nhánh **`dev`**:

```
[GIT PUSH / MERGE DEV]
          │
          ▼
┌─────────────────────────┐
│ Job 1: Fast Validation  │ ⚡ Compile core-model + backend-service
│ (Maven Cache)           │ (Bỏ unit test để tiết kiệm quota 2000 phút/tháng)
└─────────┬───────────────┘
          │
          ▼
┌─────────────────────────┐
│ Job 2: Build & Push     │ 🐳 Docker Buildx multi-stage
│ (GHCR Registry)         │ Cache layer trên GitHub Cache -> Push ghcr.io
└─────────┬───────────────┘
          │
          ▼
┌─────────────────────────┐
│ Job 3: SSH Deploy       │ 🚀 SSH vào 163.61.72.183
│ & Automated Rollback    │ Chạy deploy.sh:
└─────────────────────────┘   1. Lưu mốc stable tag hiện tại
                              2. Pull image mới (tag sha-xxxxxx)
                              3. docker compose up -d backend-service
                              4. Chờ Health Check (/actuator/health)
                              5. NẾU FAIL -> TỰ ĐỘNG ROLLBACK VỀ TAG TRƯỚC!
```

---

## 3. Cấu hình GitHub Secrets (Chỉ làm 1 lần)

Truy cập: **GitHub Repository → Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Giá trị | Mô tả |
|---|---|---|
| `SSH_HOST` | `163.61.72.183` | IP server triển khai |
| `SSH_USER` | `devops` (hoặc `root`) | User SSH trên server |
| `SSH_PRIVATE_KEY` | *(Nội dung Private Key)* | Private key để Actions SSH vào server |
| `DISCORD_WEBHOOK` | `https://discord.com/api/webhooks/...` | Webhook URL kênh Discord nhận thông báo kết quả |
| `GH_PAT` *(Tùy chọn)* | `ghp_...` (Quyền `repo`, `read:packages`) | Dùng khi `core-model` là private repo |

> **Lưu ý:** `GITHUB_TOKEN` đã được cấp quyền tự động bởi GitHub Actions để push Docker image lên GitHub Packages (GHCR).

---

## 4. Cấu hình Server để Pull Image từ GHCR (Chỉ làm 1 lần)

Trên server `163.61.72.183`, đăng nhập GHCR để docker pull được image:

```bash
ssh root@163.61.72.183

# Đăng nhập GHCR bằng GitHub Username và Personal Access Token (PAT)
echo "YOUR_GITHUB_PAT" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

---

## 5. Quy trình Rollback & Xử lý sự cố

### 5.1. Rollback tự động (Automated Rollback)
Nếu container mới gặp lỗi (crash, DB không kết nối được, hoặc không pass health check sau 90 giây):
- Script `deploy.sh` sẽ **tự động phát hiện lỗi**, in 40 dòng logs gần nhất.
- Tự động gọi `docker compose` để khôi phục lại image tag trước đó từ file `.last-stable-tag`.
- Pipeline GitHub Actions trả về trạng thái **Failed** để thông báo cho đội ngũ.

### 5.2. Rollback thủ công 1 chạm (Manual Rollback)
Nếu phát hiện bug nghiệp vụ sau khi đã deploy thành công:

```bash
ssh root@163.61.72.183
cd ~/backend-service

# Cách 1: Khôi phục tự động về phiên bản stable gần nhất
sudo bash deploy/scripts/rollback.sh

# Cách 2: Khôi phục về 1 tag SHA cụ thể
sudo bash deploy/scripts/rollback.sh sha-a1b2c3d ghcr.io/f-b-chain-erp/erp-backend
```

---

## 6. Quy trình Triển khai Thủ công Dự phòng (Local-First Fallback)

Giữ nguyên quy trình cũ để phòng khi server mất kết nối Internet hoặc GitHub gặp sự cố:

### Bước 1: Khởi động Hạ tầng
```bash
ssh root@163.61.72.183
cd ~/backend-service
chmod +x deploy/scripts/*.sh
sudo bash deploy/scripts/01-server-infra.sh
```

### Bước 2: Kiểm tra kết nối từ Local
Từ máy local dev, mở DBeaver / DataGrip hoặc terminal:
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
Trong `application-dev.yaml`, cấu hình đã mặc định trỏ về IP server `163.61.72.183`. Chạy backend:
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
### Bước 2: Build & Load Image từ Local (Windows PowerShell)
```powershell
# Từ thư mục gốc ERP-UTT trên máy Local
.\backend-service\deploy\scripts\02-build-push.ps1 -Tag "latest"
```

### Bước 3: Khởi chạy trên Server
```bash
ssh root@163.61.72.183
cd ~/backend-service
sudo bash deploy/scripts/03-deploy-app.sh latest
```

---

## 7. Các lệnh Quản trị thường dùng trên Server

```bash
cd ~/backend-service

# Xem logs backend realtime
docker logs -f erp-backend

# Xem logs database postgres
docker logs -f erp-postgres

# Xem logs redis
docker logs -f erp-redis

# Xem trạng thái tất cả containers kèm health check
docker compose -f src/main/docker/app.yml ps

# Kiểm tra endpoint sức khỏe
curl http://localhost:8080/actuator/health

# Khởi động lại riêng backend
docker compose -f src/main/docker/app.yml restart backend-service

# Khởi động lại riêng hạ tầng (Postgres / Redis)
docker compose -f src/main/docker/infra.yml restart

# Dừng toàn bộ hệ thống
docker compose -f src/main/docker/app.yml down
```
