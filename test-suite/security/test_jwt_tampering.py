#!/usr/bin/env python3
# ==============================================================================
# BẢO MẬT 02: KIỂM THỬ XÁC THỰC TOKEN & GIẢ MẠO QUYỀN (JWT TAMPERING & INTEGRITY)
# Mục tiêu: Đảm bảo JwtAuthFilterChain và JwtAuthenticationEntryPoint chặn đứng:
#   1. Sửa đổi payload JWT (giả mạo role admin, đổi user ID) mà giữ nguyên chữ ký.
#   2. Tấn công 'alg: none' (Unsigned JWT bypass).
#   3. Token ký bằng Secret Key giả mạo.
#   4. Token hết hạn (Expired Token).
#   5. Token rác / chuỗi ký tự dị thường (Malformed Token).
# ==============================================================================

import os
import sys
import time
import jwt
import requests

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
TEST_USERNAME = os.getenv("TEST_USERNAME", "test_user_0001")
TEST_PASSWORD = os.getenv("TEST_PASSWORD", "Password@123")


def log_pass(msg):
    print(f"  [PASS] \033[92m{msg}\033[0m")


def log_fail(msg):
    print(f"  [FAIL] \033[91m{msg}\033[0m")


def log_info(msg):
    print(f"  [*] {msg}")


def get_legit_token():
    try:
        r = requests.post(f"{BASE_URL}/api/v1/auth/login", json={
            "username": TEST_USERNAME,
            "password": TEST_PASSWORD
        }, timeout=5)
        if r.status_code == 200:
            data = r.json()
            return data.get("data", {}).get("access_token") or data.get("access_token")
    except Exception as e:
        print(f"Không thể lấy token hợp lệ: {e}")
    return None


def test_jwt_tampering():
    print("==================================================================")
    print("  ERP-UTT SECURITY SUITE: JWT TAMPERING & TOKEN VERIFICATION")
    print(f"  Target Server: {BASE_URL}")
    print("==================================================================")

    protected_url = f"{BASE_URL}/api/v1/accounts/me"
    legit_token = get_legit_token()

    if not legit_token:
        log_info("Không lấy được token hợp lệ từ server, tạo token giả lập để test...")
        legit_token = jwt.encode({"sub": "test_user_0001", "exp": int(time.time()) + 3600}, "fake-secret", algorithm="HS256")

    # Decode payload
    try:
        decoded_payload = jwt.decode(legit_token, options={"verify_signature": False})
    except Exception:
        decoded_payload = {"sub": "test_user_0001"}

    scenarios = []

    # 1. Payload tampering: Đổi role thành ROLE_ADMIN nhưng giữ signature cũ
    parts = legit_token.split(".")
    if len(parts) == 3:
        tampered_payload = dict(decoded_payload)
        tampered_payload["roles"] = ["ROLE_ADMIN", "SYSTEM_ADMIN"]
        tampered_payload["scope"] = "ALL_SYSTEM"
        import base64
        import json
        new_payload_b64 = base64.urlsafe_b64encode(json.dumps(tampered_payload).encode()).decode().rstrip("=")
        tampered_token = f"{parts[0]}.{new_payload_b64}.{parts[2]}"
        scenarios.append(("1. Giả mạo Payload (Leo thang quyền ADMIN, giữ signature cũ)", tampered_token))

    # 2. 'alg': 'none' attack
    header_none = {"alg": "none", "typ": "JWT"}
    unsigned_token = jwt.encode(decoded_payload, key="", algorithm="none", headers=header_none) if hasattr(jwt, "encode") else ""
    # Nếu thư viện pyjwt không cho phép encode none, tạo thủ công:
    import base64
    import json
    h_b64 = base64.urlsafe_b64encode(b'{"alg":"none","typ":"JWT"}').decode().rstrip("=")
    p_b64 = base64.urlsafe_b64encode(json.dumps(decoded_payload).encode()).decode().rstrip("=")
    none_token = f"{h_b64}.{p_b64}."
    scenarios.append(("2. Tấn công Unsigned Token (alg: none)", none_token))

    # 3. Ký bằng Secret Key giả mạo
    fake_key_token = jwt.encode(decoded_payload, "malicious_attacker_secret_key_1234567890", algorithm="HS256")
    scenarios.append(("3. Token ký bằng Secret Key giả mạo của kẻ tấn công", fake_key_token))

    # 4. Token hết hạn (Expired Token)
    expired_payload = dict(decoded_payload)
    expired_payload["exp"] = int(time.time()) - 3600  # Hết hạn 1 giờ trước
    expired_token = jwt.encode(expired_payload, "ZGV2LW9ubHktc2VjcmV0LW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHMtbG9uZy1mb3ItaG1hYw==", algorithm="HS256")
    scenarios.append(("4. Token hợp lệ nhưng đã hết hạn (Expired Token)", expired_token))

    # 5. Token dị dạng (Malformed Token)
    scenarios.append(("5. Chuỗi token rác (Malformed Garbage String)", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.INVALID_PAYLOAD_HERE"))
    scenarios.append(("6. Header Authorization rỗng", ""))

    # Thực thi kiểm thử
    for name, token in scenarios:
        headers = {
            "Authorization": f"Bearer {token}" if token else "",
            "User-Agent": "security-jwt-tester/1.0"
        }
        try:
            resp = requests.get(protected_url, headers=headers, timeout=5)
            print(f"\n▶ Thử nghiệm: \033[94m{name}\033[0m")
            if resp.status_code == 401:
                log_pass(f"Hệ thống từ chối chính xác: HTTP 401 Unauthorized (Message: {resp.json().get('message', 'Rejected')})")
            elif resp.status_code == 403:
                log_pass(f"Hệ thống chặn truy cập: HTTP 403 Forbidden")
            elif resp.status_code == 500:
                log_fail(f"Hệ thống bị sập luồng (HTTP 500)! Cần kiểm tra xử lý Exception tại JwtAuthFilterChain.")
            else:
                log_fail(f"LỖ HỔNG NGUY HIỂM! Request được chấp nhận với mã HTTP {resp.status_code}!")
        except Exception as e:
            log_fail(f"Lỗi khi gửi request: {e}")


if __name__ == "__main__":
    test_jwt_tampering()
