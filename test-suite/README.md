# ERP-UTT POST-DEPLOYMENT TEST SUITE & SRE PLAYBOOK

Bộ công cụ kiểm thử toàn diện sau triển khai cho hệ thống **ERP-UTT (Pine Drink ERP)**.  
Bao gồm: **Seed dữ liệu quy mô lớn (100.000+ records)**, **Kiểm thử hiệu năng & chịu tải (k6)**, **Kiểm thử an toàn & bảo mật (Security Suite)** và **Giám sát thời gian thực (SRE Grafana/Prometheus Dashboard)**.

---

## 📂 CẤU TRÚC THƯ MỤC BỘ KIỂM THỬ

```
backend-service/test-suite/
├── seeder/                         # Module nạp dữ liệu lớn trực tiếp vào PostgreSQL
│   ├── requirements.txt            # Thư viện: psycopg2-binary, faker, bcrypt
│   ├── config.py                   # Cấu hình kết nối DB và chỉ tiêu số lượng records
│   └── generate_large_data.py      # Script Python batch insert tối ưu tốc độ
│
├── k6/                             # Kịch bản kiểm thử tải & sức chịu đựng bằng Grafana k6
│   ├── config.js                   # Cấu hình BASE_URL, helper lấy Token & luân chuyển VU
│   ├── users_pool.json             # Danh sách tài khoản test được sinh tự động từ seeder
│   ├── 01_baseline_load.js         # Kịch bản tải tiêu chuẩn (50 VUs - 5 phút)
│   ├── 02_stress_test.js           # Kịch bản bậc thang tìm điểm gãy (100 -> 1.000 VUs)
│   ├── 03_spike_test.js            # Kịch bản sốc tải đột ngột (Flash Sale: 50 -> 800 VUs)
│   ├── 04_soak_test.js             # Kịch bản tải ngâm đường trường (Memory/Connection Leak)
│   └── 05_race_condition_stock.js # Kịch bản tranh chấp trừ kho đồng thời (Row Lock)
│
├── security/                       # Bộ kịch bản thăm dò an toàn thông tin & bảo mật
│   ├── requirements.txt            # Thư viện: requests, PyJWT, cryptography
│   ├── test_rate_limit.py          # Kiểm thử ngưỡng chặn 429 Anonymous & Authenticated
│   ├── test_jwt_tampering.py       # Kiểm thử giả mạo token, unsigned 'none', expired
│   ├── test_idor_datascope.py      # Kiểm thử rò rỉ dữ liệu chéo chi nhánh (Data Scope)
│   ├── test_sqli_fuzzing.py        # Bắn payload SQLi vào login, search, order by
│   └── test_dos_resilience.py      # Kiểm thử DoS payload lớn (>25MB), JSON đệ quy, SSE
│
├── monitoring/                     # Công cụ giám sát SRE thời gian thực
│   ├── grafana_stress_dashboard.json # Dashboard Grafana mẫu tối ưu cho bài test tải
│   └── promql_queries_cheatsheet.md  # Sổ tay PromQL tra cứu nhanh độ trễ, pool, JVM
│
└── README.md                       # Sổ tay hướng dẫn quy trình 5 bước thực hiện
```

---

## 🚀 QUY TRÌNH 5 BƯỚC THỰC HIỆN KIỂM THỬ THỰC TẾ

### BƯỚC 1: SAO LƯU DỰ PHÒNG & KẾT NỐI HỆ THỐNG GIÁM SÁT

Trước khi chạy bất kỳ bài test nào, cần đảm bảo an toàn dữ liệu và mở kết nối theo dõi:

1. **Tạo bản sao lưu Snapshot trên Server**:
   ```bash
   # Chạy trên server:
   sudo bash deploy/scripts/backup-database.sh
   ```

2. **Khởi chạy hệ thống Monitoring (nếu chưa bật)**:
   ```bash
   sudo bash deploy/scripts/04-start-monitoring.sh
   ```

3. **Mở SSH Tunnel từ máy cá nhân để xem Grafana & Prometheus**:
   ```bash
   ssh -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 root@163.61.72.183
   ```
   - Mở trình duyệt: `http://localhost:3000` (User: `admin` | Password: `admin123456`)
   - Vào mục **Dashboards** -> **New** -> **Import** -> Chọn file:  
     `backend-service/test-suite/monitoring/grafana_stress_dashboard.json`
   - Bảng theo dõi real-time toàn diện 3 tầng (App, DB, Host) sẽ sẵn sàng!

---

### BƯỚC 2: SEED DỮ LIỆU LỚN VÀO POSTGRESQL

Script `generate_large_data.py` nạp hàng trăm ngàn bản ghi (20 chi nhánh, 500 tài khoản, 1.000 sản phẩm, 20.000 tồn kho, 50.000 đơn hàng, 50.000 audit log) chỉ trong ~30 - 60 giây nhờ cơ chế batch insert.

1. **Cài đặt thư viện Python**:
   ```bash
   cd test-suite/seeder
   pip install -r requirements.txt
   ```

2. **Chạy script seed**:
   - Nếu chạy trực tiếp trên Server (hoặc qua SSH tunnel trỏ port 5432):
     ```bash
     export DB_HOST="localhost"
     export DB_PORT="5432"
     export DB_NAME="erp_dev"
     export DB_USERNAME="postgres"
     export DB_PASSWORD="your_secure_password"
     python generate_large_data.py
     ```
   - *Lưu ý*: Script sẽ tự động xuất file `test-suite/k6/users_pool.json` chứa 1.000 tài khoản test mẫu có sẵn mật khẩu để bộ k6 luân chuyển đăng nhập.

---

### BƯỚC 3: KIỂM THỬ AN TOÀN BẢO MẬT (SECURITY SUITE)

Chạy các script kiểm tra xem hệ thống có bảo vệ đúng các quy tắc phòng thủ hay không:

```bash
cd test-suite/security
pip install -r requirements.txt

# 1. Kiểm tra Rate Limiting (20 req/min anonymous, 100 req/min user)
python test_rate_limit.py

# 2. Kiểm tra tính toàn vẹn JWT và chống giả mạo token
python test_jwt_tampering.py

# 3. Kiểm tra rò rỉ dữ liệu chéo chi nhánh (Data Scope & IDOR)
python test_idor_datascope.py

# 4. Kiểm tra chống tấn công SQL Injection và lỗi cú pháp lộ ra ngoài
python test_sqli_fuzzing.py

# 5. Kiểm tra chống DoS tài nguyên (Payload >25MB, JSON lồng, SSE)
python test_dos_resilience.py
```

> **Tiêu chuẩn nghiệm thu**: Tất cả các script trên đều phải trả về trạng thái `[PASS]` màu xanh. Không được xuất hiện bất kỳ mã lỗi `500 Internal Server Error` không được đóng gói nào.

---

### BƯỚC 4: KIỂM THỬ HIỆU NĂNG & SỨC CHỊU TẢI (K6 SUITE)

Cài đặt k6 nếu máy bạn chưa có:
- **Windows**: `winget install k6 --source winget` hoặc `choco install k6`
- **Linux / VPS**: `sudo gpg -k && sudo apt-get install k6`

Thực hiện các kịch bản kiểm thử theo thứ tự tăng tiến:

#### 1. Baseline Load Test (Tải chuẩn - 50 VUs):
```bash
cd test-suite/k6
k6 run -e BASE_URL=http://163.61.72.183 01_baseline_load.js
```
*Đo P95, đảm bảo độ trễ phản hồi < 600ms.*

#### 2. Stress Test (Thử tải cực hạn 100 -> 1.000 VUs):
```bash
k6 run -e BASE_URL=http://163.61.72.183 02_stress_test.js
```
*Mở đồng thời Grafana xem panel HikariCP và CPU để tìm thời điểm hệ thống bắt đầu nghẽn.*

#### 3. Spike Test (Sốc tải Flash Sale: 50 -> 800 VUs):
```bash
k6 run -e BASE_URL=http://163.61.72.183 03_spike_test.js
```
*Đánh giá xem Nginx và Tomcat có tự phục hồi sau khi cơn bão traffic đi qua hay không.*

#### 4. Concurrency Race Condition Test (Tranh chấp kho đồng thời):
```bash
k6 run -e BASE_URL=http://163.61.72.183 05_race_condition_stock.js
```
*50 luồng cùng lúc gọi API tạo phiếu xuất kho. Verify không có Deadlock xảy ra.*

#### 5. (Tùy chọn) Soak Test (Tải ngâm 30 phút - 2 giờ):
```bash
k6 run -e BASE_URL=http://163.61.72.183 -e SOAK_DURATION=30m 04_soak_test.js
```

---

### BƯỚC 5: ĐÁNH GIÁ KẾT QUẢ & CÁC ĐIỂM CẦN TUNING (SRE RUNBOOK)

Trong khi chạy k6, đối chiếu với các chỉ số trên Grafana Dashboard:

1. **Nếu HikariCP Pending Connections > 0 kéo dài**:
   - *Nguyên nhân*: `spring.datasource.hikari.maximum-pool-size` đang để 20, trong khi số request đồng thời quá lớn.
   - *Giải pháp*: Tăng pool size lên `30 - 40` (với điều kiện PostgreSQL `max_connections` cho phép, tối thiểu 100).

2. **Nếu CPU VPS đạt 100%**:
   - *Nguyên nhân*: Mã hóa BCrypt khi đăng nhập quá nhiều hoặc câu query SELECT phân trang thiếu index.
   - *Giải pháp*: Kiểm tra `pg_stat_statements` trên Postgres để tìm câu query có `total_exec_time` cao nhất.

3. **Nếu xuất hiện lỗi HTTP 429 khi đang chạy k6**:
   - *Nguyên nhân*: Số lượng VU lớn làm vượt hạn mức `RateLimitFilter`.
   - *Giải pháp*: Khi chạy test tải cực hạn trên môi trường staging/test, có thể cấu hình tăng tạm thời biến môi trường `RL_AUTH_CAPACITY=50000` trên container backend.
