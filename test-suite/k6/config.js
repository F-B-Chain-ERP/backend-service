// ==============================================================================
// K6 CONFIGURATION & SHARED UTILITIES CHO ERP-UTT
// ==============================================================================

import http from 'k6/http';
import { check } from 'k6';

// Địa chỉ mục tiêu (Truyền qua CLI: k6 run -e BASE_URL=http://127.0.0.1:8080 script.js)
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Đọc danh sách 10 tài khoản thật đã có trong Database
let rawUsers = [];
try {
    rawUsers = JSON.parse(open('./users_pool.json'));
} catch (e) {
    rawUsers = [
        { usernameOrEmail: "admin1", username: "admin1", password: "123456789", role: "ROLE_ADMIN", type: "ACCOUNT" },
        { usernameOrEmail: "admin2", username: "admin2", password: "123456789", role: "ROLE_ADMIN", type: "ACCOUNT" },
        { usernameOrEmail: "admin3", username: "admin3", password: "123456789", role: "ROLE_ADMIN", type: "ACCOUNT" },
        { usernameOrEmail: "admin4", username: "admin4", password: "123456789", role: "ROLE_ADMIN", type: "ACCOUNT" },
        { usernameOrEmail: "hoangdinhdung", username: "hoangdinhdung", password: "123456789", role: "ROLE_ADMIN", type: "ACCOUNT" },
        { usernameOrEmail: "hoangdinhdung20205", username: "hoangdinhdung20205", password: "123456789", role: "ROLE_MANAGER", type: "ACCOUNT" },
        { usernameOrEmail: "staff01", username: "staff01", password: "123456789", role: "ROLE_CASHIER", type: "ACCOUNT" },
        { usernameOrEmail: "user01", username: "user01", password: "123456789", role: "ROLE_USER", type: "ACCOUNT" },
        { usernameOrEmail: "test", username: "test", password: "123456789", role: "ROLE_USER", type: "ACCOUNT" },
        { usernameOrEmail: "abc", username: "abc", password: "123456789", role: "ROLE_USER", type: "ACCOUNT" }
    ];
}

export const USERS_POOL = rawUsers;

/**
 * Sinh ngẫu nhiên IP giả lập cho mỗi Virtual User (VU) qua X-Forwarded-For
 */
export function getRandomClientIp() {
    const vu = (__VU > 0 ? __VU : 1) % 250;
    const sub = Math.floor(Math.random() * 250) + 1;
    return `10.${vu}.${sub}.${Math.floor(Math.random() * 250) + 1}`;
}

/**
 * Tạo headers cho request công khai (không cần login)
 */
export function getPublicHeaders() {
    return {
        headers: {
            'Content-Type': 'application/json',
            'X-Forwarded-For': getRandomClientIp(),
            'User-Agent': 'k6-load-tester/1.0'
        },
        timeout: '10s'
    };
}

/**
 * Đăng nhập một tài khoản cụ thể và lấy JWT Access Token
 */
export function authenticateUser(userObj) {
    const loginUrl = `${BASE_URL}/api/v1/auth/login`;
    const usernameOrEmail = userObj.usernameOrEmail || userObj.username;
    const password = userObj.password || '123456789';
    const principalType = userObj.type || 'ACCOUNT';

    const payload = JSON.stringify({
        usernameOrEmail: usernameOrEmail,
        password: password,
        type: principalType
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Forwarded-For': getRandomClientIp(),
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
                return !!(body.data?.accessToken || body.data?.access_token || body.accessToken || body.access_token);
            } catch (e) {
                return false;
            }
        }
    });

    if (!success) {
        console.warn(`[AUTH FAILED] User '${usernameOrEmail}' status=${res.status}: ${res.body}`);
        return null;
    }

    const body = JSON.parse(res.body);
    return body.data?.accessToken || body.data?.access_token || body.accessToken || body.access_token;
}

/**
 * Lấy toàn bộ danh sách Token đã đăng nhập trước cho các tài khoản trong pool.
 * Được gọi một lần duy nhất trong hàm setup() của k6.
 */
export function getAllAuthenticatedTokens() {
    console.log(`[SETUP] Bắt đầu lấy JWT Token cho ${USERS_POOL.length} tài khoản trong DB...`);
    const tokens = [];

    for (let i = 0; i < USERS_POOL.length; i++) {
        const u = USERS_POOL[i];
        const token = authenticateUser(u);
        if (token) {
            tokens.push({
                username: u.usernameOrEmail,
                role: u.role,
                token: token
            });
            console.log(`  ✔ Login thành công tài khoản: '${u.usernameOrEmail}'`);
        }
    }

    console.log(`[SETUP] Hoàn tất: Lấy được ${tokens.length}/${USERS_POOL.length} Token xác thực.`);
    return tokens;
}

/**
 * Tạo headers chuẩn kèm Token xác thực
 */
export function getAuthHeaders(token) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'X-Forwarded-For': getRandomClientIp(),
            'User-Agent': 'k6-load-tester/1.0'
        },
        timeout: '15s'
    };
}
