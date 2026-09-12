// ==============================================================================
// 01: BASELINE LOAD TEST (KIỂM THỬ TẢI TIÊU CHUẨN - 50 VUs)
// Mục tiêu: Đo đạc độ trễ cơ sở (P50, P95, P99) với luồng người dùng thực tế
// sử dụng bộ 10 tài khoản đã có sẵn trong Database.
// ==============================================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { BASE_URL, getAllAuthenticatedTokens, getAuthHeaders, getPublicHeaders } from './config.js';

export const options = {
    stages: [
        { duration: '30s', target: 20 }, // Khởi động lên 20 VUs
        { duration: '3m', target: 50 },  // Duy trì ổn định 50 VUs
        { duration: '30s', target: 0 },  // Hạ tải về 0
    ],
    thresholds: {
        'http_req_duration': ['p(95)<800', 'p(99)<1500'], // P95 dưới 800ms
        'http_req_failed': ['rate<0.05'],                 // Lỗi dưới 5%
    },
};

export function setup() {
    console.log(`[START] Bắt đầu Baseline Load Test tới: ${BASE_URL}`);
    const tokens = getAllAuthenticatedTokens();
    if (tokens.length === 0) {
        console.warn('[WARN] Không có tài khoản nào đăng nhập thành công. Vui lòng kiểm tra mật khẩu!');
    }
    return { tokens };
}

export default function (data) {
    const publicHeaders = getPublicHeaders();

    // -------------------------------------------------------------------------
    // FLOW 1: KHÁCH HÀNG / CÔNG KHAI DUYỆT MENU ĐỒ UỐNG
    // -------------------------------------------------------------------------
    group('Flow 1: Public Menu Browsing', function () {
        // 1. Tải danh mục đồ uống
        const resCat = http.get(`${BASE_URL}/api/v1/menu/categories`, publicHeaders);
        check(resCat, {
            'GET /menu/categories is 200': (r) => r.status === 200,
        });

        // 2. Phân trang danh sách sản phẩm đồ uống (Query DB)
        const page = Math.floor(Math.random() * 5);
        const resProd = http.get(`${BASE_URL}/api/v1/menu/products?page=${page}&size=10`, publicHeaders);
        check(resProd, {
            'GET /menu/products is 200': (r) => r.status === 200,
        });

        // 3. Tìm kiếm sản phẩm theo từ khóa
        const keywords = ['Trà', 'Cà phê', 'Matcha', 'Sữa', 'Đào'];
        const kw = keywords[Math.floor(Math.random() * keywords.length)];
        const resSearch = http.get(`${BASE_URL}/api/v1/menu/products?keyword=${encodeURIComponent(kw)}`, publicHeaders);
        check(resSearch, {
            'GET /menu/products search is 200': (r) => r.status === 200,
        });
    });

    sleep(1);

    // -------------------------------------------------------------------------
    // FLOW 2: NHÂN VIÊN VẬN HÀNH (ĐĂNG NHẬP, TRA CỨU KHO, XEM ĐƠN MUA HÀNG)
    // -------------------------------------------------------------------------
    if (data.tokens && data.tokens.length > 0) {
        // Phân bổ đều các VU cho 10 tài khoản thật đã đăng nhập
        const session = data.tokens[__VU % data.tokens.length];
        const authHeaders = getAuthHeaders(session.token);

        group(`Flow 2: Staff Operations (${session.username})`, function () {
            // 4. Xác thực hồ sơ và quyền hạn cá nhân
            const resMe = http.get(`${BASE_URL}/api/v1/accounts/me`, authHeaders);
            check(resMe, {
                'GET /accounts/me is 200': (r) => r.status === 200,
            });

            // 5. Tra cứu tồn kho nguyên liệu các chi nhánh (Query JOIN DB nặng)
            const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=20`, authHeaders);
            check(resStock, {
                'GET /inv/stocks is 200': (r) => r.status === 200,
                'Not 429 Too Many Requests': (r) => r.status !== 429,
            });

            // 6. Tra cứu danh sách đơn mua hàng PO
            const resPo = http.get(`${BASE_URL}/api/v1/proc/purchase-orders?page=0&size=10`, authHeaders);
            check(resPo, {
                'GET /proc/purchase-orders is 200': (r) => r.status === 200,
            });
        });
    }

    sleep(Math.random() * 1.5 + 1); // Nghỉ 1 - 2.5 giây như hành vi người dùng thật
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Baseline Load Test.');
}
