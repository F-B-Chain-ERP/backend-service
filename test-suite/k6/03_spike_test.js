// ==============================================================================
// 03: SPIKE TEST (ĐỘT BIẾN TẢI ĐỘT NGỘT - FLASH SALE)
// Mục tiêu: Giả lập kịch bản lượng truy cập tăng vọt từ 50 lên 800 VUs trong 20 giây,
// duy trì 2 phút rồi hạ xuống để kiểm tra khả năng hấp thụ sốc và tự hồi phục.
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, getRandomUser, authenticate, getAuthHeaders } from './config.js';

export const options = {
    stages: [
        { duration: '30s', target: 50 },   // Trạng thái bình thường: 50 VUs
        { duration: '20s', target: 800 },  // BÙNG NỔ: Vọt lên 800 VUs trong 20s!
        { duration: '2m', target: 800 },   // Giữ tải đỉnh trong 2 phút
        { duration: '30s', target: 50 },   // Hạ tải về mức bình thường
        { duration: '1m', target: 50 },    // Theo dõi xem hệ thống có tự phục hồi không
    ],
    thresholds: {
        'http_req_failed': ['rate<0.20'], // Đánh giá tỷ lệ sống sót trong cơn bão traffic
    },
};

export function setup() {
    console.log(`[SPIKE] Bắt đầu kiểm thử đột biến tải tại: ${BASE_URL}`);
    const tokens = [];
    for (let i = 0; i < 20; i++) {
        const u = getRandomUser();
        const t = authenticate(u.username, u.password);
        if (t) tokens.push(t);
    }
    return { tokens };
}

export default function (data) {
    const token = data.tokens.length > 0
        ? data.tokens[Math.floor(Math.random() * data.tokens.length)]
        : null;

    // Mô phỏng người dùng ùa vào xem danh mục khuyến mãi & đặt đồ uống
    const resMenu = http.get(`${BASE_URL}/api/v1/menu/products?is_featured=true`);
    check(resMenu, {
        'Spike menu responds': (r) => r.status !== 0,
        'No 502/504 Bad Gateway': (r) => r.status !== 502 && r.status !== 504,
    });

    if (token) {
        const headers = getAuthHeaders(token);
        const resCat = http.get(`${BASE_URL}/api/v1/menu/categories`, headers);
        check(resCat, {
            'Spike category query ok': (r) => r.status === 200,
        });
    }

    sleep(0.3);
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Spike Test.');
}
