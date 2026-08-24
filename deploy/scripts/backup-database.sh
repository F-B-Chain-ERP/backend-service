#!/usr/bin/env bash
# ==============================================================================
# SCRIPT TỰ ĐỘNG SAO LƯU POSTGRESQL, KIỂM TRA TÍNH TOÀN VẸN VÀ OFFSITE SYNC
# ==============================================================================
set -e

BACKUP_DIR="/var/backups/erp-postgres"
RETENTION_DAYS=14
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/erp_backup_${TIMESTAMP}.sql.gz"
CONTAINER_NAME="erp-postgres"
DB_USER="erp_user"
DB_NAME="erp_dev"
DISCORD_WEBHOOK="${DISCORD_WEBHOOK:-}"
S3_BUCKET="${BACKUP_S3_BUCKET:-}"

# Hàm gửi cảnh báo lỗi lên Discord
send_discord_alert() {
    local status="$1"
    local message="$2"
    if [ -n "$DISCORD_WEBHOOK" ]; then
        local color=15158332 # Red
        if [ "$status" = "SUCCESS" ]; then
            color=3066993 # Green
        fi
        curl -s -H "Content-Type: application/json" -X POST -d "{
            \"embeds\": [{
                \"title\": \"💾 [ERP Database Backup] ${status}\",
                \"description\": \"${message}\",
                \"color\": ${color},
                \"fields\": [
                    {\"name\": \"Database\", \"value\": \"\`${DB_NAME}\`\", \"inline\": true},
                    {\"name\": \"File\", \"value\": \"\`$(basename "$BACKUP_FILE")\`\", \"inline\": true},
                    {\"name\": \"Host\", \"value\": \"\`$(hostname)\`\", \"inline\": true}
                ],
                \"timestamp\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"
            }]
        }" "$DISCORD_WEBHOOK" > /dev/null || true
    fi
}

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] ▶ 1. Bắt đầu quá trình sao lưu Database ${DB_NAME}..."

# 1. Trích xuất dump và nén stream qua gzip
if ! docker exec -t "${CONTAINER_NAME}" pg_dump -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists | gzip > "${BACKUP_FILE}"; then
    echo "[$(date)] [ERROR] Lỗi trong quá trình pg_dump!" >&2
    send_discord_alert "FAILED" "Lỗi thực thi lệnh pg_dump từ container ${CONTAINER_NAME}."
    exit 1
fi

# 2. KIỂM TRA TÍNH TOÀN VẸN FILE NÉN (INTEGRITY CHECK)
echo "[$(date)] ▶ 2. Kiểm tra tính toàn vẹn của bản sao lưu..."
if [ ! -s "${BACKUP_FILE}" ]; then
    echo "[$(date)] [ERROR] File backup tạo ra bị rỗng (0 bytes)!" >&2
    send_discord_alert "FAILED" "File backup tạo ra bị rỗng (0 bytes). Dung lượng ổ đĩa có thể đã đầy."
    rm -f "${BACKUP_FILE}"
    exit 1
fi

if ! gzip -t "${BACKUP_FILE}"; then
    echo "[$(date)] [ERROR] File backup bị lỗi cấu trúc gzip (Corrupted file)!" >&2
    send_discord_alert "FAILED" "File backup bị lỗi cấu trúc gzip (Corrupted file), không thể giải nén."
    rm -f "${BACKUP_FILE}"
    exit 1
fi

FILESIZE=$(ls -lh "${BACKUP_FILE}" | awk '{print $5}')
echo "[$(date)] [OK] File backup hợp lệ. Dung lượng: ${FILESIZE}"

# 3. ĐỒNG BỘ OFFSITE BACKUP (S3 / Rclone / Remote Cloud Storage)
if [ -n "$S3_BUCKET" ] && command -v aws &> /dev/null; then
    echo "[$(date)] ▶ 3. Đồng bộ bản backup lên Cloud Storage (${S3_BUCKET})..."
    if aws s3 cp "${BACKUP_FILE}" "s3://${S3_BUCKET}/postgres/${TIMESTAMP}_$(basename "$BACKUP_FILE")"; then
        echo "[$(date)] [OK] Offsite backup thành công lên S3."
    else
        echo "[$(date)] [WARN] Đồng bộ S3 thất bại. Bản backup cục bộ vẫn an toàn." >&2
    fi
elif command -v rclone &> /dev/null; then
    echo "[$(date)] ▶ 3. Đồng bộ bản backup qua Rclone..."
    rclone copy "${BACKUP_FILE}" remote:erp-backups/postgres/ || true
fi

# 4. Xóa các file backup cũ vượt quá số ngày quy định
echo "[$(date)] ▶ 4. Dọn dẹp các bản backup cũ hơn ${RETENTION_DAYS} ngày..."
find "${BACKUP_DIR}" -name "erp_backup_*.sql.gz" -type f -mtime +"${RETENTION_DAYS}" -exec rm -f {} +

echo "[$(date)] [HOÀN TẤT] Tiến trình backup thành công: ${BACKUP_FILE}"
send_discord_alert "SUCCESS" "Bản sao lưu hoàn tất và đã được kiểm tra tính toàn vẹn (Size: ${FILESIZE})."
