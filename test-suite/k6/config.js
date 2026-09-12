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
 * Lấy danh sách Token đã đăng nhập trước cho các tài khoản trong pool.
 * Được gọi một lần duy nhất trong hàm setup() của k6.
 */
export function getAllAuthenticatedTokens(maxCount = 100) {
    const targetPool = USERS_POOL.slice(0, maxCount);
    console.log(`[SETUP] Bắt đầu lấy JWT Token cho ${targetPool.length} tài khoản trong DB...`);
    const tokens = [];

    for (let i = 0; i < targetPool.length; i++) {
        const u = targetPool[i];
        const token = authenticateUser(u);
        if (token) {
            tokens.push({
                username: u.usernameOrEmail || u.username,
                role: u.role || 'ROLE_USER',
                branchId: u.branch_id || null,
                branchCode: u.branch_code || null,
                token: token
            });
            if (i < 10 || (i + 1) % 25 === 0 || i === targetPool.length - 1) {
                console.log(`  ✔ [${i + 1}/${targetPool.length}] Login: '${u.usernameOrEmail || u.username}' (${u.role || 'USER'})`);
            }
        }
    }

    console.log(`[SETUP] Hoàn tất: Thu thập được ${tokens.length}/${targetPool.length} Token xác thực.`);
    return tokens;
}

/**
 * Lọc danh sách token theo vai trò
 */
export function filterTokensByRole(tokens, roleSubstring) {
    const filtered = tokens.filter(t => t.role && t.role.toUpperCase().includes(roleSubstring.toUpperCase()));
    return filtered.length > 0 ? filtered : tokens; // Fallback to all if none found
}

/**
 * Tạo headers chuẩn kèm Token xác thực và mã chi nhánh (X-Branch-Id)
 */
export function getAuthHeaders(token, branchId = null) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'X-Forwarded-For': getRandomClientIp(),
        'User-Agent': 'k6-load-tester/1.0'
    };
    if (branchId) {
        headers['X-Branch-Id'] = branchId;
    }
    return {
        headers: headers,
        timeout: '15s'
    };
}
