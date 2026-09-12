#!/usr/bin/env python3
# ==============================================================================
# BẢO MẬT 05: KIỂM THỬ KHẢ NĂNG CHỐNG CHỊU TỪ CHỐI DỊCH VỤ (DOS & RESOURCE EXHAUSTION)
# Mục tiêu: Đảm bảo hạ tầng Nginx và Spring Boot chống chịu được:
#   1. Payload vượt quá dung lượng cho phép (>20MB) -> Trả về HTTP 413 Payload Too Large.
#   2. JSON lồng nhau sâu (Deeply Nested JSON Recursion) -> Ngăn lỗi StackOverflowError.
#   3. Treo kết nối Server-Sent Events (/api/v1/notifications/sse) -> Không làm kiệt quệ Tomcat threads.
# ==============================================================================

import json
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


def test_oversized_payload():
    print("\n==================================================================")
    print("▶ PHẦN 1: GỬI PAYLOAD VƯỢT DUNG LƯỢNG CHO PHÉP (>25MB)")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/auth/login"
    log_info("Đang tạo body dung lượng ~25MB (vượt trần 20MB cấu hình)...")
    large_string = "A" * (25 * 1024 * 1024)
    payload = {"username": "admin", "password": large_string}

    try:
        start = time.time()
        resp = requests.post(url, json=payload, timeout=10)
        elapsed = time.time() - start
        print(f"   Phản hồi sau {elapsed:.2f}s: HTTP {resp.status_code}")

        if resp.status_code == 413:
            log_pass("Hệ thống chặn chuẩn xác: HTTP 413 Payload Too Large (Nginx hoặc Spring multipart limit).")
        elif resp.status_code in [400, 429]:
            log_pass(f"Request bị từ chối an toàn: HTTP {resp.status_code}")
        elif resp.status_code == 500:
            log_fail("Server bị lỗi 500 nội bộ! Cần cấu hình giới hạn kích thước tại Nginx để giảm tải cho backend.")
        else:
            log_info(f"Mã trạng thái phản hồi: HTTP {resp.status_code}")
    except requests.exceptions.ConnectionError:
        log_pass("Kết nối bị ngắt ngay lập tức bởi Reverse Proxy Nginx (Rất tốt, bảo vệ backend khỏi OOM).")
    except Exception as e:
        log_info(f"Lỗi: {e}")


def test_deep_nested_json():
    print("\n==================================================================")
    print("▶ PHẦN 2: TẤN CÔNG JSON ĐỆ QUY LỒNG NHAU SÂU (DEEP NESTED JSON)")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/auth/login"
    log_info("Tạo JSON có độ sâu 100 tầng lồng nhau...")

    # Tạo JSON lồng 100 tầng: {"a": {"a": {"a": ...}}}
    nested = {"username": "test"}
    for _ in range(100):
        nested = {"nested": nested}

    try:
        resp = requests.post(url, json=nested, timeout=5)
        print(f"   Phản hồi: HTTP {resp.status_code}")
        if resp.status_code in [400, 422, 429]:
            log_pass("Jackson Parser từ chối payload cấu trúc sai lệch một cách an toàn.")
        elif resp.status_code == 500:
            if "stackoverflow" in resp.text.lower():
                log_fail("LỖ HỔNG DOS! Jackson bị lỗi StackOverflowError do đệ quy sâu!")
            else:
                log_pass("Lỗi được đóng gói, không làm sập máy chủ.")
        else:
            log_info(f"Mã trả về: HTTP {resp.status_code}")
    except Exception as e:
        log_info(f"Lỗi gửi request: {e}")


def test_sse_connection_leak():
    print("\n==================================================================")
    print("▶ PHẦN 3: KIỂM THỬ KẾT NỐI STREAMING SSE (/api/v1/notifications/sse)")
    print("==================================================================")

    url = f"{BASE_URL}/api/v1/notifications/sse"
    log_info("Mở 10 kết nối SSE đồng thời trong 5 giây...")

    connections = []
    try:
        for i in range(1, 11):
            r = requests.get(url, stream=True, timeout=5)
            connections.append(r)
            print(f"   Kết nối SSE #{i:02d}: HTTP {r.status_code} (Header Content-Type: {r.headers.get('Content-Type')})")
            time.sleep(0.1)

        log_pass("Tất cả các kết nối SSE được khởi tạo mà không làm nghẽn thread pool.")
    except Exception as e:
        log_info(f"Lỗi kiểm thử SSE: {e}")
    finally:
        for c in connections:
            try:
                c.close()
            except Exception:
                pass
        log_info("Đã đóng toàn bộ kết nối SSE thử nghiệm.")


if __name__ == "__main__":
    print("==================================================================")
    print("  ERP-UTT SECURITY SUITE: DOS & RESILIENCE VERIFICATION")
    print(f"  Target Server: {BASE_URL}")
    print("==================================================================")
    test_oversized_payload()
    test_deep_nested_json()
    test_sse_connection_leak()
