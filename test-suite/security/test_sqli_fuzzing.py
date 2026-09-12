#!/usr/bin/env python3
# ==============================================================================
# BẢO MẬT 04: KIỂM THỬ TẤN CÔNG SQL INJECTION & THĂM DÒ FUZZING
# Mục tiêu: Bắn các payload SQL Injection vào các tham số tìm kiếm, phân trang
# sắp xếp (sort), và payload đăng nhập để chứng minh Spring Data JPA / Hibernate
# đã tham số hóa (Parameterized Queries) hoàn toàn và không làm lộ stack trace.
# ==============================================================================

import os
import time
import requests

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")


def log_pass(msg):
    print(f"  [PASS] \033[92m{msg}\033[0m")


def log_fail(msg):
    print(f"  [FAIL] \033[91m{msg}\033[0m")


def log_info(msg):
    print(f"  [*] {msg}")


SQLI_PAYLOADS = [
    "' OR '1'='1",
    "1' OR '1' = '1' --",
    "' UNION SELECT null, null, null, null --",
    "'; DROP TABLE test_dummy; --",
    "' OR 1=1 LIMIT 1; --",
    "admin'--",
    "' OR SLEEP(3) --",
    "'; SELECT pg_sleep(3); --",
    "1 AND (SELECT 1 FROM (SELECT pg_sleep(3))a)",
    "\" OR \"\"=\"",
]


def test_sqli_on_login():
    print("\n==================================================================")
    print("▶ PHẦN 1: SQL INJECTION TRÊN API AUTHENTICATION (/api/v1/auth/login)")
    print("==================================================================")

    login_url = f"{BASE_URL}/api/v1/auth/login"
    for p in SQLI_PAYLOADS[:5]:
        start = time.time()
        try:
            resp = requests.post(login_url, json={"username": p, "password": "random_password"}, timeout=6)
            elapsed = time.time() - start
            print(f"   Payload: \033[93m{p}\033[0m -> HTTP {resp.status_code} ({elapsed:.2f}s)")

            # Kiểm tra xem có bypass thành công (200 OK) không
            if resp.status_code == 200:
                log_fail(f"LỖ HỔNG CỰC KỲ NGUY HIỂM! Bypass login thành công với payload: {p}")
            elif resp.status_code in [400, 401, 429]:
                log_pass("Từ chối truy cập an toàn, không có hành vi bypass.")
            elif resp.status_code == 500:
                # Kiểm tra xem có để lộ lỗi SQL trong body không
                text = resp.text.lower()
                if "syntax error" in text or "postgresql" in text or "org.postgresql" in text:
                    log_fail("Lộ thông tin nội bộ PostgreSQL trong phản hồi HTTP 500!")
                else:
                    log_pass("Trả về lỗi 500 được đóng gói chuẩn, không lộ cú pháp SQL.")
        except requests.exceptions.Timeout:
            log_fail(f"Nghi vấn Time-based Blind SQLi! Request bị treo do SLEEP: {p}")
        except Exception as e:
            log_info(f"Lỗi kết nối: {e}")


def test_sqli_on_search_and_filter():
    print("\n==================================================================")
    print("▶ PHẦN 2: SQL INJECTION TRÊN QUERY PARAMS & TÌM KIẾM SẢN PHẨM")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/menu/products"

    for p in SQLI_PAYLOADS:
        params = {"keyword": p}
        start = time.time()
        try:
            resp = requests.get(url, params=params, timeout=5)
            elapsed = time.time() - start
            print(f"   Param keyword=\033[93m{p}\033[0m -> HTTP {resp.status_code} ({elapsed:.2f}s)")

            if elapsed >= 3.0:
                log_fail(f"CẢNH BÁO TIME-BASED SQLi! Độ trễ phản hồi {elapsed:.2f}s nghi vấn thực thi lệnh SLEEP!")
            elif resp.status_code == 200:
                # Phải là kết quả rỗng hoặc lọc chuỗi bình thường
                log_pass("JPA đã tham số hóa an toàn (Prepared Statement).")
            elif resp.status_code in [400, 422]:
                log_pass("Tham số bị chặn tại tầng Validation.")
            elif resp.status_code == 500:
                if "syntax error" in resp.text.lower():
                    log_fail("Phát hiện lỗi cú pháp SQL lộ ra phía client!")
                else:
                    log_pass("Lỗi server được bắt an toàn.")
        except requests.exceptions.Timeout:
            log_fail(f"Nghi vấn Time-based Blind SQLi: {p}")
        except Exception as e:
            log_info(f"Lỗi gửi request: {e}")


def test_sqli_on_sort_params():
    print("\n==================================================================")
    print("▶ PHẦN 3: SQL INJECTION QUA THAM SỐ SẮP XẾP (ORDER BY INJECTION)")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/menu/products"
    sort_payloads = [
        "id; SELECT pg_sleep(3)--",
        "id, (CASE WHEN (1=1) THEN id ELSE name END)",
        "id ASC; DROP TABLE test--",
    ]

    for sp in sort_payloads:
        params = {"sort": sp}
        start = time.time()
        try:
            resp = requests.get(url, params=params, timeout=5)
            elapsed = time.time() - start
            print(f"   Sort Param: \033[93m{sp}\033[0m -> HTTP {resp.status_code} ({elapsed:.2f}s)")
            if elapsed >= 3.0:
                log_fail(f"Nghi vấn Order By Time-based SQLi: {sp}")
            elif resp.status_code in [200, 400, 500]:
                if "syntax error" in resp.text.lower():
                    log_fail("Phát hiện lỗi cú pháp SQL trên mệnh đề ORDER BY!")
                else:
                    log_pass("Không có biểu hiện bị tiêm mã độc qua tham số sort.")
        except Exception as e:
            log_info(f"Lỗi: {e}")


if __name__ == "__main__":
    print("==================================================================")
    print("  ERP-UTT SECURITY SUITE: SQL INJECTION & PARAMETER FUZZING")
    print(f"  Target Server: {BASE_URL}")
    print("==================================================================")
    test_sqli_on_login()
    test_sqli_on_search_and_filter()
    test_sqli_on_sort_params()
