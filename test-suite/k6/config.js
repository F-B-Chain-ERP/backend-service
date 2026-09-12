// ==============================================================================
// K6 CONFIGURATION & SHARED UTILITIES CHO ERP-UTT
// ==============================================================================

import http from 'k6/http';
import { check } from 'k6';

// Địa chỉ mục tiêu (Có thể truyền từ dòng lệnh: k6 run -e BASE_URL=http://163.61.72.183 script.js)
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Đọc pool tài khoản đã seed (nếu không có thì dùng fallback mặc định)
let rawUsers = null;
try {
    rawUsers = JSON.parse(open('./users_pool.json'));
} catch (e) {
    rawUsers = [
        { username: 'test_user_0001', password: 'Password@123' },
        { username: 'test_user_0002', password: 'Password@123' },
        { username: 'test_user_0003', password: 'Password@123' },
        { username: 'test_user_0004', password: 'Password@123' },
        { username: 'test_user_0005', password: 'Password@123' }
    ];
}

export const USERS_POOL = rawUsers;

/**
 * Lấy ngẫu nhiên một tài khoản từ pool để luân chuyển JWT Token,
 * tránh bị RateLimitFilter khóa do chạm trần 100 requests/user/phút.
 */
export function getRandomUser() {
    const idx = Math.floor(Math.random() * USERS_POOL.length);
    return USERS_POOL[idx];
}

/**
 * Đăng nhập và lấy JWT Access Token
 */
export function authenticate(username, password) {
    const loginUrl = `${BASE_URL}/api/v1/auth/login`;
    const payload = JSON.stringify({
        username: username,
        password: password || 'Password@123'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'User-Agent': 'k6-load-tester/1.0'
        },
        timeout: '10s'
    };

    const res = http.post(loginUrl, payload, params);

    const success = check(res, {
        'login status is 200': (r) => r.status === 200,
        'has access_token': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body && (body.data?.access_token || body.access_token || body.data?.accessToken);
            } catch (e) {
                return false;
            }
        }
    });

    if (!success) {
        return null;
    }

    const body = JSON.parse(res.body);
    return body.data?.access_token || body.access_token || body.data?.accessToken;
}

/**
 * Tạo headers chuẩn kèm Token xác thực
 */
export function getAuthHeaders(token) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'User-Agent': 'k6-load-tester/1.0'
        },
        timeout: '15s'
    };
}
