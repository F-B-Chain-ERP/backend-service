// ==============================================================================
// 01: BASELINE LOAD TEST (KIỂM THỬ TẢI TIÊU CHUẨN)
// Mục tiêu: Đo đạc độ trễ cơ sở (Latency P50, P95, P99) và lượng tài nguyên
// tiêu thụ ở mức tải thông thường (50 Virtual Users - VUs).
// ==============================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { BASE_URL, getRandomUser, authenticate, getAuthHeaders } from './config.js';

export const options = {
    stages: [
        { duration: '1m', target: 20 },  // Khởi động tăng dần lên 20 VUs
        { duration: '3m', target: 50 },  // Duy trì ổn định 50 VUs trong 3 phút
        { duration: '1m', target: 0 },   // Hạ tải về 0
    ],
    thresholds: {
        'http_req_duration': ['p(95)<600', 'p(99)<1200'], // 95% request phản hồi dưới 600ms
        'http_req_failed': ['rate<0.01'],                // Tỷ lệ lỗi dưới 1%
    },
};

export function setup() {
    console.log(`[START] Bắt đầu Baseline Load Test tới: ${BASE_URL}`);
    // Chuẩn bị sẵn 10 token đại diện
    const tokens = [];
    for (let i = 0; i < 10; i++) {
        const user = getRandomUser();
        const t = authenticate(user.username, user.password);
        if (t) tokens.push(t);
    }
    console.log(`[SETUP] Đã tạo sẵn ${tokens.length} token xác thực cho các VU.`);
    return { tokens };
}

export default function (data) {
    const token = data.tokens.length > 0 
        ? data.tokens[Math.floor(Math.random() * data.tokens.length)] 
        : null;

    group('01. Public Browsing (Menu & Products)', function () {
        // 1. Duyệt danh mục đồ uống
        const resCat = http.get(`${BASE_URL}/api/v1/menu/categories`);
        check(resCat, {
            'GET /categories is 200': (r) => r.status === 200,
        });

        // 2. Phân trang danh sách sản phẩm
        const page = Math.floor(Math.random() * 5);
        const resProd = http.get(`${BASE_URL}/api/v1/menu/products?page=${page}&size=10`);
        check(resProd, {
            'GET /products is 200': (r) => r.status === 200,
        });

        // 3. Tìm kiếm sản phẩm
        const keywords = ['Trà', 'Cà phê', 'Matcha', 'Sữa'];
        const kw = keywords[Math.floor(Math.random() * keywords.length)];
        const resSearch = http.get(`${BASE_URL}/api/v1/menu/products?keyword=${encodeURIComponent(kw)}`);
        check(resSearch, {
            'GET /products search is 200': (r) => r.status === 200,
        });
    });

    sleep(1);

    if (token) {
        group('02. Authenticated Queries (Inventory & Accounts)', function () {
            const authHeaders = getAuthHeaders(token);

            // 4. Tra cứu tồn kho (Query DB nặng)
            const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks`, authHeaders);
            check(resStock, {
                'GET /api/v1/inv/stocks is 200': (r) => r.status === 200,
            });

            // 5. Tra cứu danh sách đơn mua PO
            const resPo = http.get(`${BASE_URL}/api/v1/proc/purchase-orders`, authHeaders);
            check(resPo, {
                'GET /api/v1/proc/purchase-orders is 200': (r) => r.status === 200,
            });
        });
    }

    sleep(Math.random() * 2 + 1); // Nghỉ từ 1 - 3 giây mô phỏng thao tác người dùng
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Baseline Load Test.');
}
