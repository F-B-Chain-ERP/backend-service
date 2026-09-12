// ==============================================================================
// 02: STRESS TEST (THỬ TẢI CỰC HẠN - 100 ĐẾN 1.000 VUs)
// Mục tiêu: Bậc thang tăng dần 100 -> 300 -> 600 -> 1000 VUs sử dụng 10 tài khoản thật
// để ép bão hòa CPU, Tomcat Threads và HikariCP Connection Pool (20 kết nối).
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, getAllAuthenticatedTokens, getAuthHeaders, getPublicHeaders } from './config.js';

export const options = {
    stages: [
        { duration: '1m', target: 100 },  // Bậc 1: 100 VUs
        { duration: '2m', target: 300 },  // Bậc 2: 300 VUs
        { duration: '2m', target: 600 },  // Bậc 3: 600 VUs
        { duration: '2m', target: 1000 }, // Bậc 4: Đẩy lên đỉnh 1.000 VUs
        { duration: '1m', target: 0 },    // Hạ tải phục hồi
    ],
    thresholds: {
        'http_req_duration': ['p(95)<3000'], // Ngưỡng cảnh báo độ trễ P95 > 3s
        'http_req_failed': ['rate<0.20'],    // Tỷ lệ lỗi tối đa chấp nhận được khi stress cực hạn
    },
};

export function setup() {
    console.log(`[START] Bắt đầu Stress Test tới: ${BASE_URL}`);
    const tokens = getAllAuthenticatedTokens();
    return { tokens };
}

export default function (data) {
    const publicHeaders = getPublicHeaders();

    // 1. Luồng duyệt Menu công khai
    const resMenu = http.get(`${BASE_URL}/api/v1/menu/products?page=0&size=20`, publicHeaders);
    check(resMenu, {
        'Menu status is 200': (r) => r.status === 200,
        'Menu not 5xx': (r) => r.status < 500,
    });

    // 2. Luồng phân trang tìm kiếm nặng
    const randomPage = Math.floor(Math.random() * 10);
    const resPaged = http.get(`${BASE_URL}/api/v1/menu/products?page=${randomPage}&size=30`, publicHeaders);
    check(resPaged, {
        'Paged status is 200': (r) => r.status === 200,
    });

    // 3. Luồng nghiệp vụ Nhân viên & Quản trị (Có Token)
    if (data.tokens && data.tokens.length > 0) {
        const session = data.tokens[__VU % data.tokens.length];
        const authHeaders = getAuthHeaders(session.token);

        // Tra cứu tồn kho (Query DB đè nặng lên HikariCP)
        const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=20`, authHeaders);
        check(resStock, {
            'Stock status is 200': (r) => r.status === 200,
            'No 429 Too Many Requests': (r) => r.status !== 429,
        });

        // Tra cứu danh sách đơn mua PO
        const resPo = http.get(`${BASE_URL}/api/v1/proc/purchase-orders?page=0&size=10`, authHeaders);
        check(resPo, {
            'PO status is 200': (r) => r.status === 200,
        });
    }

    sleep(Math.random() * 0.5 + 0.2); // Nghỉ ngắn để duy trì áp lực cao liên tục
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Stress Test.');
}
