# SỔ TAY CÂU TRUY VẤN PROMQL & GIÁM SÁT REAL-TIME (SRE METRICS CHEATSHEET)

Tài liệu tra cứu nhanh các câu lệnh **PromQL** để theo dõi hiệu năng hệ thống trên **Prometheus UI (`http://localhost:9090/graph`)** hoặc gán vào **Grafana Panels** khi đang chạy bài kiểm thử tải và bảo mật.

---

## 1. Tầng Ứng Dụng (Spring Boot Actuator - Job: `erp-backend-service`)

### 1.1. Throughput (Tốc độ xử lý Request/giây - RPS)
```promql
sum(rate(http_server_requests_seconds_count{application="backend-service"}[1m])) by (status)
```
> **Ý nghĩa**: Đếm số request hoàn thành mỗi giây phân theo mã HTTP (200, 400, 429, 500,...). Nếu xuất hiện dải màu của mã 5xx hoặc 429 tăng vọt, hệ thống đang gặp lỗi hoặc kích hoạt Rate Limit.

### 1.2. Độ trễ phản hồi theo phân vị (Latency Percentiles P50, P95, P99)
```promql
# P95 Latency (95% người dùng nhận phản hồi dưới mức này)
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="backend-service"}[1m])) by (le))

# P99 Latency (Độ trễ của 1% request chậm nhất)
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application="backend-service"}[1m])) by (le))
```
> **Cảnh báo**: Nếu P95 vượt quá 1.500ms (1.5 giây), ứng dụng đang bị nghẽn CPU hoặc nghẽn Connection Pool DB.

### 1.3. Connection Pool Database (HikariCP Saturation)
```promql
# Số kết nối đang bận xử lý query
hikaricp_connections_active{pool="HikariPool-1"}

# Số luồng đang PHẢI XẾP HÀNG CHỜ lấy kết nối (CỰC KỲ QUAN TRỌNG)
hikaricp_connections_pending{pool="HikariPool-1"}

# Số kết nối rảnh rỗi trong pool
hikaricp_connections_idle{pool="HikariPool-1"}
```
> **Ngưỡng nguy hiểm**: Nếu `hikaricp_connections_active` đạt 20/20 liên tục và `hikaricp_connections_pending` > 0 trong nhiều giây, pool kết nối đã cạn kiệt!

### 1.4. Bộ nhớ JVM & Garbage Collection (GC)
```promql
# Dung lượng RAM Heap đang sử dụng
jvm_memory_used_bytes{application="backend-service", area="heap"}

# Thời gian ứng dụng bị dừng do GC (Stop-The-World Pause)
rate(jvm_gc_pause_seconds_sum{application="backend-service"}[1m])
```
> **Dấu hiệu rò rỉ (Memory Leak)**: Đồ thị Heap tăng dần hình răng cưa nhưng đáy dốc sau mỗi lần GC ngày càng dâng cao và không hạ xuống được.

### 1.5. Luồng xử lý Tomcat (Tomcat Worker Threads)
```promql
tomcat_threads_busy_threads{application="backend-service"}
tomcat_threads_current_threads{application="backend-service"}
```

---

## 2. Tầng Cơ Sở Dữ Liệu (PostgreSQL - Job: `erp-postgres-exporter`)

### 2.1. Số lượng kết nối Database thực tế
```promql
pg_stat_activity_count
```

### 2.2. Khóa dòng và Deadlocks
```promql
# Tốc độ xảy ra Deadlock
rate(pg_stat_database_deadlocks[1m])
```
> **Tiêu chuẩn**: Trong mọi bài test, số lượng Deadlock phải bằng `0`. Nếu > 0, cần rà soát lại thứ tự acquire lock trong các transaction trừ kho hoặc đặt hàng.

### 2.3. Tỷ lệ đọc đệm trong RAM (Database Cache Hit Ratio)
```promql
sum(rate(pg_stat_database_blks_hit[1m])) / 
(sum(rate(pg_stat_database_blks_hit[1m])) + sum(rate(pg_stat_database_blks_read[1m]))) * 100
```
> **Tiêu chuẩn**: Phải duy trì trên **95%**. Nếu rớt xuống dưới 80%, PostgreSQL đang phải đọc trực tiếp từ ổ cứng SSD, gây nghẽn I/O Disk nghiêm trọng.

### 2.4. Số giao dịch Commit vs Rollback mỗi giây
```promql
rate(pg_stat_database_xact_commit[1m])
rate(pg_stat_database_xact_rollback[1m])
```

---

## 3. Tầng Bộ Nhớ Đệm & Token (Redis - Job: `erp-redis-exporter`)

### 3.1. Throughput Redis (Lệnh xử lý mỗi giây - Ops/sec)
```promql
rate(redis_commands_processed_total[1m])
```

### 3.2. Dung lượng RAM Redis đang sử dụng
```promql
redis_memory_used_bytes
```

### 3.3. Tỷ lệ trúng Cache (Redis Cache Hit vs Miss Ratio)
```promql
rate(redis_keyspace_hits_total[1m]) / 
(rate(redis_keyspace_hits_total[1m]) + rate(redis_keyspace_misses_total[1m])) * 100
```

### 3.4. Số Key bị ép xóa do hết RAM (Evicted Keys)
```promql
rate(redis_evicted_keys_total[1m])
```
> **Ý nghĩa**: Nếu chỉ số này > 0, Redis đã chạm trần `maxmemory` và đang phải xóa các token/cache cũ.

---

## 4. Tầng Máy Chủ Hạ Tầng (Node Exporter & cAdvisor)

### 4.1. Mức sử dụng CPU toàn hệ thống (%)
```promql
100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[1m])) * 100)
```

### 4.2. Mức sử dụng RAM VPS (%)
```promql
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
```

### 4.3. Swap Usage (Cảnh báo hết RAM)
```promql
node_memory_SwapTotal_bytes - node_memory_SwapFree_bytes
```
> **Nguy hiểm**: Nếu Swap > 0, hệ thống đang phải dùng ổ cứng làm RAM, hiệu năng sẽ suy giảm 100 lần.

### 4.4. Đọc/Ghi Ổ Cứng (Disk I/O Write Throughput)
```promql
rate(node_disk_written_bytes_total[1m])
```
