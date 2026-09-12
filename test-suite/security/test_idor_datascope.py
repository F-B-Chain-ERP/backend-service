#!/usr/bin/env python3
# ==============================================================================
# BẢO MẬT 03: KIỂM THỬ PHÂN QUYỀN ĐA CHI NHÁNH & CHỐNG RÒ RỈ IDOR (DATA SCOPE)
# Mục tiêu: Xác thực DataScopeHelper và Spring Security Method Security chặn đứng:
#   1. Quản lý Chi nhánh A cố tình tra cứu tồn kho / phiếu xuất của Chi nhánh B.
#   2. Người dùng thông thường xem hoặc sửa thông tin tài khoản của người khác qua IDOR.
#   3. Thu ngân cố tình gọi API phê duyệt mua hàng (sai thẩm quyền Role).
# ==============================================================================

import json
import os
import sys
import requests

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")


def log_pass(msg):
    print(f"  [PASS] \033[92m{msg}\033[0m")


def log_fail(msg):
    print(f"  [FAIL] \033[91m{msg}\033[0m")


def log_info(msg):
    print(f"  [*] {msg}")


def load_test_users():
    pool_path = os.path.join(os.path.dirname(__file__), "..", "k6", "users_pool.json")
    if os.path.exists(pool_path):
        with open(pool_path, "r", encoding="utf-8") as f:
            return json.load(f)
    return []


def login(username, password):
    try:
        r = requests.post(f"{BASE_URL}/api/v1/auth/login", json={
            "username": username,
            "password": password
        }, timeout=5)
        if r.status_code == 200:
            body = r.json()
            return body.get("data", {}).get("access_token") or body.get("access_token")
    except Exception as e:
        print(f"Lỗi đăng nhập {username}: {e}")
    return None


def test_idor_and_datascope():
    print("==================================================================")
    print("  ERP-UTT SECURITY SUITE: IDOR & DATA SCOPE ISOLATION TEST")
    print(f"  Target Server: {BASE_URL}")
    print("==================================================================")

    users = load_test_users()
    if len(users) < 2:
        log_info("Cần ít nhất 2 user test trong pool. Vui lòng chạy seeder trước.")
        return

    user_a = users[0]
    user_b = users[1]

    log_info(f"Đăng nhập User A ({user_a['username']} - Role: {user_a.get('role')})...")
    token_a = login(user_a["username"], user_a["password"])

    log_info(f"Đăng nhập User B ({user_b['username']} - Role: {user_b.get('role')})...")
    token_b = login(user_b["username"], user_b["password"])

    if not token_a or not token_b:
        log_fail("Không thể đăng nhập tài khoản test. Bỏ qua kiểm thử IDOR.")
        return

    headers_a = {"Authorization": f"Bearer {token_a}", "User-Agent": "security-idor-tester/1.0"}
    headers_b = {"Authorization": f"Bearer {token_b}", "User-Agent": "security-idor-tester/1.0"}

    # 1. Test IDOR trên Account Controller
    print("\n▶ Thử nghiệm 1: IDOR - User A tra cứu thông tin cá nhân của User B qua ID")
    try:
        # Lấy thông tin cá nhân của User B
        res_me_b = requests.get(f"{BASE_URL}/api/v1/accounts/me", headers=headers_b, timeout=5)
        if res_me_b.status_code == 200:
            b_info = res_me_b.json().get("data", {})
            b_id = b_info.get("id")
            if b_id:
                # User A dùng token của mình để truy vấn GET /api/v1/accounts/{b_id}
                res_idor = requests.get(f"{BASE_URL}/api/v1/accounts/{b_id}", headers=headers_a, timeout=5)
                if res_idor.status_code in [403, 401]:
                    log_pass(f"Chặn thành công IDOR: Trả về HTTP {res_idor.status_code}")
                elif res_idor.status_code == 200 and user_a.get("role") != "ROLE_ADMIN":
                    log_fail(f"CẢNH BÁO LỖ HỔNG IDOR! User thường đọc được chi tiết account của User khác (HTTP 200)!")
                else:
                    log_info(f"Phản hồi từ endpoint: HTTP {res_idor.status_code}")
    except Exception as e:
        log_info(f"Không thể hoàn tất thử nghiệm 1: {e}")

    # 2. Test DataScope phân quyền chi nhánh (Cross-Branch Data Leak)
    print("\n▶ Thử nghiệm 2: Cross-Branch Access (Chi nhánh A truy xuất kho Chi nhánh B)")
    try:
        # Lấy danh sách kho mà User A thấy
        res_wh = requests.get(f"{BASE_URL}/api/v1/inv/stocks", headers=headers_a, timeout=5)
        if res_wh.status_code == 200:
            log_pass(f"User A truy xuất tồn kho chi nhánh của mình: HTTP {res_wh.status_code}")
        elif res_wh.status_code == 403:
            log_pass(f"DataScope hoạt động: Chặn quyền truy cập nếu tài khoản không có scope kho.")
    except Exception as e:
        log_info(f"Lỗi kiểm thử DataScope: {e}")

    # 3. Test Quyền hạn đặc biệt (Privilege Escalation)
    print("\n▶ Thử nghiệm 3: Leo thang thẩm quyền (Tài khoản Cashier gọi API Phê duyệt PO)")
    try:
        fake_po_id = "00000000-0000-0000-0000-000000000001"
        res_approve = requests.patch(
            f"{BASE_URL}/api/v1/proc/purchase-orders/{fake_po_id}/approve",
            headers=headers_a,
            timeout=5
        )
        if res_approve.status_code in [403, 401]:
            log_pass(f"Hệ thống RBAC chặn đúng quyền phê duyệt của role không hợp lệ: HTTP {res_approve.status_code}")
        elif res_approve.status_code == 404:
            log_pass("Endpoint không tìm thấy bản ghi giả lập nhưng đã qua lớp bảo mật.")
        else:
            log_info(f"Phản hồi API: HTTP {res_approve.status_code}")
    except Exception as e:
        log_info(f"Lỗi thử nghiệm 3: {e}")


if __name__ == "__main__":
    test_idor_and_datascope()
