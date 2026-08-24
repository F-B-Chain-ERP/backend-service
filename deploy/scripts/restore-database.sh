#!/usr/bin/env bash
# ==============================================================================
# SCRIPT PHỤC HỒI DỮ LIỆU POSTGRESQL (DISASTER RECOVERY / DATABASE RESTORE)
# ==============================================================================
set -e

BACKUP_DIR="/var/backups/erp-postgres"
CONTAINER_NAME="erp-postgres"
DB_USER="erp_user"
DB_NAME="erp_dev"

# Tham số đầu vào: Đường dẫn file backup (nếu không truyền sẽ lấy bản mới nhất)
TARGET_FILE="$1"
FORCE_RESTORE=false

if [ "$2" = "-f" ] || [ "$2" = "--force" ] || [ "$1" = "-f" ] || [ "$1" = "--force" ]; then
    FORCE_RESTORE=true
fi

if [ -z "$TARGET_FILE" ] || [ "$TARGET_FILE" = "-f" ] || [ "$TARGET_FILE" = "--force" ]; then
    TARGET_FILE=$(find "${BACKUP_DIR}" -name "erp_backup_*.sql.gz" -type f | sort -r | head -n 1)
    if [ -z "$TARGET_FILE" ]; then
        echo "[ERROR] Không tìm thấy file backup nào trong thư mục ${BACKUP_DIR}!" >&2
        exit 1
    fi
    echo "▶ Tự động chọn bản backup mới nhất: ${TARGET_FILE}"
fi

# 1. Kiểm tra sự tồn tại của file
if [ ! -f "${TARGET_FILE}" ]; then
    echo "[ERROR] File sao lưu không tồn tại: ${TARGET_FILE}" >&2
    exit 1
fi

# 2. Kiểm tra tính toàn vẹn của file nén trước khi restore
echo "▶ 1. Kiểm tra tính toàn vẹn của file nén..."
if ! gzip -t "${TARGET_FILE}"; then
    echo "[ERROR] File backup bị hỏng cấu trúc (Corrupted gzip). Hủy bỏ tiến trình khôi phục!" >&2
    exit 1
fi
echo "[OK] File backup toàn vẹn và hợp lệ."

# 3. Kiểm tra container PostgreSQL có đang chạy và sẵn sàng không
echo "▶ 2. Kiểm tra container database..."
if ! docker exec "${CONTAINER_NAME}" pg_isready -U "${DB_USER}" -d "${DB_NAME}" > /dev/null 2>&1; then
    echo "[ERROR] Database container ${CONTAINER_NAME} không ở trạng thái sẵn sàng!" >&2
    exit 1
fi

# 4. Xác nhận từ người vận hành (nếu không có cờ --force)
if [ "$FORCE_RESTORE" = false ]; then
    echo ""
    echo "=================================================================="
    echo "  ⚠️ CẢNH BÁO NGUY HIỂM: TIẾN TRÌNH GHI ĐÈ TOÀN BỘ DỮ LIỆU!"
    echo "  Database đích : ${DB_NAME}"
    echo "  File nguồn    : ${TARGET_FILE}"
    echo "=================================================================="
    read -p "Bạn có chắc chắn muốn khôi phục và ghi đè database? (gõ 'YES' để xác nhận): " CONFIRM
    if [ "$CONFIRM" != "YES" ]; then
        echo "[ABORTED] Đã hủy tiến trình khôi phục theo yêu cầu người dùng."
        exit 0
    fi
fi

# 5. Ngắt tất cả kết nối hiện tại để tránh Deadlock khi restore
echo "▶ 3. Ngắt các kết nối active tới database ${DB_NAME}..."
docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d postgres -c "
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" > /dev/null 2>&1 || true

# 6. Tiến hành nạp dữ liệu từ file backup
echo "▶ 4. Bắt đầu khôi phục dữ liệu vào ${DB_NAME}..."
START_TIME=$(date +%s)

if gunzip -c "${TARGET_FILE}" | docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" > /tmp/restore_db.log 2>&1; then
    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    echo "[OK] Khôi phục dữ liệu thành công trong ${DURATION} giây."
else
    echo "[ERROR] Lỗi trong quá trình import dữ liệu! Kiểm tra chi tiết tại /tmp/restore_db.log" >&2
    exit 1
fi

# 7. Kiểm tra số lượng tables sau khi khôi phục
echo "▶ 5. Kiểm tra tính sẵn sàng của dữ liệu..."
TABLE_COUNT=$(docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" -t -c "
SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")

echo "=================================================================="
echo "  [HOÀN TẤT] PHỤC HỒI DỮ LIỆU THÀNH CÔNG!"
echo "  - Tổng số bảng (tables) : $(echo "$TABLE_COUNT" | xargs)"
echo "  - Database              : ${DB_NAME}"
echo "  - Nguồn sao lưu         : $(basename "${TARGET_FILE}")"
echo "=================================================================="
