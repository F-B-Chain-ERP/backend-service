// ==============================================================================
// 02: STRESS TEST (THỬ TẢI CỰC HẠN - TÌM ĐIỂM GÃY BREAKING POINT)
// Mục tiêu: Đẩy tải tăng dần theo bậc thang từ 100 -> 300 -> 600 -> 1000 VUs
// để xác định ngưỡng sập nguồn, tràn connection pool (HikariCP) hoặc nghẽn CPU/RAM.
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, getRandomUser, authenticate, getAuthHeaders } from './config.js';

export const options = {
    stages: [
        { duration: '2m', target: 100 },  // Bậc 1: 100 VUs
        { duration: '2m', target: 300 },  // Bậc 2: 300 VUs
        { duration: '2m', target: 600 },  // Bậc 3: 600 VUs
        { duration: '2m', target: 1000 }, // Bậc 4: Đẩy lên đỉnh 1.000 VUs
        { duration: '2m', target: 0 },    // Hạ nhiệt phục hồi hệ thống
    ],
    thresholds: {
        // Cho phép quan sát phản ứng khi tải cực hạn
        'http_req_duration': ['p(95)<2500'], // Ngưỡng cảnh báo nếu P95 vượt quá 2.5s
        'http_req_failed': ['rate<0.15'],    // Tỷ lệ lỗi tối đa chấp nhận được dưới 15% khi stress
    },
};

export function setup() {
    console.log(`[START] Bắt đầu Stress Test tới: ${BASE_URL}`);
    const tokens = [];
    // Chuẩn bị 30 token khác nhau để luân chuyển
    for (let i = 0; i < 30; i++) {
        const u = getRandomUser();
        const t = authenticate(u.username, u.password);
        if (t) tokens.push(t);
    }
    console.log(`[SETUP] Đã khởi tạo ${tokens.length} token cho Stress Test.`);
    return { tokens };
}

export default function (data) {
    const token = data.tokens.length > 0
        ? data.tokens[Math.floor(Math.random() * data.tokens.length)]
        : null;

    // 1. Truy vấn Menu công khai
    const resMenu = http.get(`${BASE_URL}/api/v1/menu/products?page=0&size=20`);
    check(resMenu, {
        'Menu status is 200': (r) => r.status === 200,
        'Menu response not 5xx': (r) => r.status < 500,
    });

    // 2. Truy vấn API phân trang nặng
    const randomPage = Math.floor(Math.random() * 20);
    const resPaged = http.get(`${BASE_URL}/api/v1/menu/products?page=${randomPage}&size=50`);
    check(resPaged, {
        'Paged status is 200': (r) => r.status === 200,
    });

    // 3. Truy vấn API Nghiệp vụ cần Token (nếu có)
    if (token) {
        const headers = getAuthHeaders(token);
        const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks`, headers);
        check(resStock, {
            'Stock status is 200': (r) => r.status === 200,
            'No 429 Too Many Requests': (r) => r.status !== 429,
        });

        // 4. Tra cứu danh sách đơn mua PO
        const resPo = http.get(`${BASE_URL}/api/v1/proc/purchase-orders`, headers);
        check(resPo, {
            'PO status is 200': (r) => r.status === 200,
        });
    }

    // Thời gian nghỉ ngắn để tạo áp lực liên tục lên Thread Pool
    sleep(Math.random() * 0.5 + 0.2);
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Stress Test.');
}
