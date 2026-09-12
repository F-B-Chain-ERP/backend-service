// ==============================================================================
// 04: SOAK / ENDURANCE TEST (TẢI NGÂM ĐƯỜNG TRƯỜNG)
// Mục tiêu: Giữ tải ổn định 100 VUs liên tục (30 phút đến 2 giờ)
// để phát hiện hiện tượng rò rỉ bộ nhớ (JVM Memory Leak), rò rỉ kết nối DB
// (HikariCP Connection Leak) hoặc phình bộ nhớ Redis theo thời gian.
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, getAllAuthenticatedTokens, getAuthHeaders, getPublicHeaders } from './config.js';

const TEST_DURATION = __ENV.SOAK_DURATION || '30m';

export const options = {
    stages: [
        { duration: '2m', target: 100 },            // Tăng lên 100 VUs
        { duration: TEST_DURATION, target: 100 },   // Ngâm tải liên tục
        { duration: '2m', target: 0 },              // Hạ tải
    ],
    thresholds: {
        'http_req_duration': ['p(95)<800'],
        'http_req_failed': ['rate<0.02'],
    },
};

export function setup() {
    console.log(`[SOAK] Bắt đầu Soak Test trong ${TEST_DURATION} tại: ${BASE_URL}`);
    const tokens = getAllAuthenticatedTokens();
    return { tokens };
}

export default function (data) {
    const token = data.tokens && data.tokens.length > 0
        ? data.tokens[__VU % data.tokens.length].token
        : null;

    const publicHeaders = getPublicHeaders();

    // 1. Duyệt sản phẩm
    http.get(`${BASE_URL}/api/v1/menu/products?page=1&size=20`, publicHeaders);

    // 2. Tra cứu tồn kho
    if (token) {
        const headers = getAuthHeaders(token);
        const res = http.get(`${BASE_URL}/api/v1/inv/stocks`, headers);
        check(res, {
            'Soak req status is 200': (r) => r.status === 200,
        });
    }

    sleep(2);
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Soak Test.');
}
