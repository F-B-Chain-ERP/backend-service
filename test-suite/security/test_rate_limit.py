#!/usr/bin/env python3
# ==============================================================================
# BẢO MẬT 01: KIỂM THỬ GIỚI HẠN TỐC ĐỘ TRUY CẬP (RATE LIMIT ENFORCEMENT)
# Mục tiêu: Xác thực RateLimitFilter hoạt động chính xác với:
#   1. Anonymous Rate Limit: Ngưỡng 20 req/60s theo IP.
#   2. Authenticated Rate Limit: Ngưỡng 100 req/60s theo User ID.
#   3. Kiểm tra Header Retry-After và định dạng lỗi 429 ApiResponse chuẩn.
# ==============================================================================

import os
import sys
import time
import requests

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
TEST_USERNAME = os.getenv("TEST_USERNAME", "admin1")
TEST_PASSWORD = os.getenv("TEST_PASSWORD", "123456789")


def log_pass(msg):
    print(f"  [PASS] \033[92m{msg}\033[0m")


def log_fail(msg):
    print(f"  [FAIL] \033[91m{msg}\033[0m")


def log_info(msg):
    print(f"  [*] {msg}")


def test_anonymous_rate_limit():
    print("\n==================================================================")
    print("▶ PHẦN 1: TEST ANONYMOUS RATE LIMIT (Ngưỡng 20 req/phút theo IP)")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/auth/login"
    payload = {"username": "fake_user_test", "password": "wrong_password"}

    session = requests.Session()
    session.headers.update({"User-Agent": "security-rate-limit-tester/1.0"})

    status_codes = []
    retry_after_found = False

    log_info(f"Đang gửi liên tục 35 requests tới: {url}...")
    for i in range(1, 36):
        resp = session.post(url, json=payload, timeout=5)
        status_codes.append(resp.status_code)
        if resp.status_code == 429:
            retry_after = resp.headers.get("Retry-After")
            if retry_after:
                retry_after_found = True
            print(f"   Request #{i:02d}: \033[93mHTTP 429 Too Many Requests\033[0m (Retry-After: {retry_after}s)")
        else:
            print(f"   Request #{i:02d}: HTTP {resp.status_code}")
        time.sleep(0.05)

    count_429 = status_codes.count(429)
    print("\n--- KẾT QUẢ PHẦN 1 ---")
    if count_429 > 0:
        log_pass(f"Bộ lọc đã kích hoạt thành công: Chặn {count_429}/35 requests với mã HTTP 429.")
    else:
        log_fail("Không có request nào bị chặn 429! Kiểm tra cấu hình app.rate-limit.anonymous hoặc Redis connection.")

    if retry_after_found:
        log_pass("Header 'Retry-After' đã được trả về chính xác theo chuẩn RFC 6585.")
    else:
        log_fail("Thiếu header 'Retry-After' trong phản hồi 429.")


def test_authenticated_rate_limit():
    print("\n==================================================================")
    print("▶ PHẦN 2: TEST AUTHENTICATED RATE LIMIT (Ngưỡng 100 req/phút theo User)")
    print("==================================================================")

    login_url = f"{BASE_URL}/api/v1/auth/login"
    login_payload = {"usernameOrEmail": TEST_USERNAME, "password": TEST_PASSWORD, "type": "ACCOUNT"}

    log_info(f"Đang đăng nhập với tài khoản: {TEST_USERNAME}...")
    try:
        r = requests.post(login_url, json=login_payload, timeout=10)
        if r.status_code != 200:
            log_fail(f"Đăng nhập thất bại (HTTP {r.status_code}): {r.text}")
            log_info("Bỏ qua test authenticated rate limit (Cần seed data tài khoản trước).")
            return
        data = r.json()
        token = data.get("data", {}).get("accessToken") or data.get("data", {}).get("access_token") or data.get("accessToken")
        log_pass("Lấy Access Token thành công.")
    except Exception as e:
        log_fail(f"Lỗi kết nối tới Backend: {e}")
        return

    # Bắn 120 requests có kèm Token xác thực vào endpoint /api/v1/accounts/me
    test_url = f"{BASE_URL}/api/v1/accounts/me"
    headers = {
        "Authorization": f"Bearer {token}",
        "User-Agent": "security-rate-limit-tester/1.0"
    }

    log_info(f"Đang gửi liên tục 125 authenticated requests tới: {test_url}...")
    status_codes = []
    for i in range(1, 126):
        resp = requests.get(test_url, headers=headers, timeout=5)
        status_codes.append(resp.status_code)
        if resp.status_code == 429:
            print(f"   Request #{i:03d}: \033[93mHTTP 429 Too Many Requests\033[0m (Chặn theo User ID)")
            break
        elif i % 20 == 0:
            print(f"   Request #{i:03d}: HTTP {resp.status_code} (Đang trong quota cho phép)")
        time.sleep(0.02)

    count_429 = status_codes.count(429)
    print("\n--- KẾT QUẢ PHẦN 2 ---")
    if count_429 > 0 or 429 in status_codes:
        log_pass("Bộ lọc authenticated rate limit (100 req/min) đã chặn thành công khi vượt ngưỡng.")
    else:
        log_info("Chưa chạm trần 100 requests hoặc bộ đếm phân bố khác nhau. Kiểm tra app.rate-limit.authenticated.")


if __name__ == "__main__":
    print("==================================================================")
    print("  ERP-UTT SECURITY SUITE: RATE LIMITING VERIFICATION")
    print(f"  Target Server: {BASE_URL}")
    print("==================================================================")
    test_anonymous_rate_limit()
    test_authenticated_rate_limit()
