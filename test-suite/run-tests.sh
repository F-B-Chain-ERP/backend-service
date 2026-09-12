#!/usr/bin/env bash
# ==============================================================================
# SCRIPT CHẠY BỘ TEST SUITE ERP-UTT TỰ ĐỘNG TRÊN LINUX / SERVER (BASH & DOCKER)
# Cách dùng:
#   chmod +x run-tests.sh
#   ./run-tests.sh [action] [target_url]
# Ví dụ:
#   ./run-tests.sh baseline http://127.0.0.1:8080
#   ./run-tests.sh security http://163.61.72.183
# Actions: seeder, security, baseline, stress, spike, race, all
# ==============================================================================

set -e

ACTION="${1:-baseline}"
TARGET="${2:-http://localhost:8080}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-erp_dev}"
DB_USER="${DB_USERNAME:-postgres}"
DB_PASS="${DB_PASSWORD:-postgres}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=========================================================="
echo "  ERP-UTT AUTOMATED TEST RUNNER (BASH / DOCKER)"
echo "  Target URL: $TARGET"
echo "  Action:     $ACTION"
echo "=========================================================="

run_k6() {
    local script_file="$1"
    echo -e "\n▶ Đang chạy k6 script: $script_file..."
    if command -v k6 &> /dev/null; then
        k6 run -e BASE_URL="$TARGET" "$SCRIPT_DIR/k6/$script_file"
    else
        echo "   k6 chưa cài đặt native, tự động dùng Docker image grafana/k6..."
        docker run --rm -i \
            --network host \
            -v "$SCRIPT_DIR/k6:/scripts" \
            -e "BASE_URL=$TARGET" \
            grafana/k6 run "/scripts/$script_file"
    fi
}

run_python() {
    local subdir="$1"
    local script_file="$2"
    echo -e "\n▶ Đang chạy Python script: $script_file..."
    if command -v python3 &> /dev/null; then
        (cd "$SCRIPT_DIR/$subdir" && pip3 install -q -r requirements.txt && BASE_URL="$TARGET" DB_HOST="$DB_HOST" DB_PORT="$DB_PORT" DB_NAME="$DB_NAME" DB_USERNAME="$DB_USER" DB_PASSWORD="$DB_PASS" python3 "$script_file")
    else
        echo "   Python3 chưa cài đặt, tự động dùng Docker python:3.12-slim..."
        docker run --rm -i \
            --network host \
            -v "$SCRIPT_DIR/$subdir:/app" \
            -w /app \
            -e "BASE_URL=$TARGET" \
            -e "DB_HOST=$DB_HOST" \
            -e "DB_PORT=$DB_PORT" \
            -e "DB_NAME=$DB_NAME" \
            -e "DB_USERNAME=$DB_USER" \
            -e "DB_PASSWORD=$DB_PASS" \
            python:3.12-slim \
            sh -c "pip install -q -r requirements.txt && python $script_file"
    fi
}

case "$ACTION" in
    seeder)
        run_python "seeder" "generate_large_data.py"
        ;;
    security)
        echo -e "\n--- BẮT ĐẦU CHẠY SECURITY SUITE ---"
        run_python "security" "test_rate_limit.py"
        run_python "security" "test_jwt_tampering.py"
        run_python "security" "test_idor_datascope.py"
        run_python "security" "test_sqli_fuzzing.py"
        run_python "security" "test_dos_resilience.py"
        ;;
    baseline)
        run_k6 "01_baseline_load.js"
        ;;
    stress)
        run_k6 "02_stress_test.js"
        ;;
    spike)
        run_k6 "03_spike_test.js"
        ;;
    race)
        run_k6 "05_race_condition_stock.js"
        ;;
    all)
        echo -e "\n=== BƯỚC 1: NẠP DỮ LIỆU LỚN ==="
        run_python "seeder" "generate_large_data.py"

        echo -e "\n=== BƯỚC 2: KIỂM THỬ BẢO MẬT ==="
        run_python "security" "test_rate_limit.py"
        run_python "security" "test_jwt_tampering.py"
        run_python "security" "test_idor_datascope.py"
        run_python "security" "test_sqli_fuzzing.py"

        echo -e "\n=== BƯỚC 3: KIỂM THỬ TẢI TIÊU CHUẨN ==="
        run_k6 "01_baseline_load.js"

        echo -e "\n=== BƯỚC 4: THỬ TẢI CỰC HẠN & TRANH CHẤP ==="
        run_k6 "05_race_condition_stock.js"
        run_k6 "02_stress_test.js"
        ;;
    *)
        echo "Action không hợp lệ: $ACTION. Chọn: seeder, security, baseline, stress, spike, race, all"
        exit 1
        ;;
esac

echo -e "\n=========================================================="
echo "  HOÀN TẤT THỰC THI KIỂM THỬ!"
echo "=========================================================="
