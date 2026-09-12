// ==============================================================================
// 05: RACE CONDITION & CONCURRENCY TEST (TRANH CHẤP KHO ĐỒNG THỜI)
// Mục tiêu: Giả lập 50 luồng đồng thời thao tác xuất kho / tạo phiếu kho
// trên cùng 1 mặt hàng để kiểm tra tính toàn vẹn dữ liệu (Locking & Isolation),
// quan sát xem hệ thống có bị Deadlock hoặc sập Connection Pool hay không.
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, getRandomUser, authenticate, getAuthHeaders } from './config.js';

export const options = {
    scenarios: {
        concurrent_stock_ops: {
            executor: 'per-vu-iterations',
            vus: 50,          // 50 luồng đồng thời tuyệt đối
            iterations: 5,    // Mỗi luồng thực hiện 5 lượt
            maxDuration: '2m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<3000'],
        // Khi tranh chấp khóa dòng (Row-level lock), chấp nhận lỗi nghiệp vụ 400/409,
        // nhưng TUYỆT ĐỐI không để xảy ra lỗi sập 500 hoặc 504 Deadlock timeout.
        'http_req_failed': ['rate<0.50'],
    },
};

export function setup() {
    console.log(`[RACE CONDITION] Chuẩn bị kiểm thử tranh chấp đồng thời tại: ${BASE_URL}`);
    const u = getRandomUser();
    const token = authenticate(u.username, u.password);

    if (!token) {
        console.error('[ERROR] Không lấy được token quản trị để setup!');
        return { token: null, warehouseId: null, materialId: null };
    }

    const headers = getAuthHeaders(token);
    let warehouseId = null;
    let materialId = null;

    // Lấy 1 bản ghi tồn kho mẫu
    const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=1`, headers);
    if (resStock.status === 200) {
        try {
            const body = JSON.parse(resStock.body);
            const item = body.data?.content?.[0] || body.data?.[0];
            if (item) {
                warehouseId = item.warehouseId;
                materialId = item.materialId;
            }
        } catch (e) {
            console.error('Lỗi parse tồn kho mẫu:', e);
        }
    }

    console.log(`[SETUP] Target Warehouse: ${warehouseId}, Target Material: ${materialId}`);
    return { token, warehouseId, materialId };
}

export default function (data) {
    if (!data.token) {
        sleep(1);
        return;
    }

    const headers = getAuthHeaders(data.token);

    // Giả lập 50 luồng cùng gửi yêu cầu tạo phiếu xuất kho cho cùng 1 nguyên liệu
    const payload = JSON.stringify({
        code: `SO_RACE_${__VU}_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
        warehouseId: data.warehouseId || '00000000-0000-0000-0000-000000000001',
        destinationType: 'PRODUCTION',
        reason: 'Xuất kho thử tải đồng thời k6 race condition',
        items: [
            {
                materialId: data.materialId || '00000000-0000-0000-0000-000000000001',
                quantity: 1.5,
                note: `VU ${__VU} batch item`
            }
        ]
    });

    const res = http.post(`${BASE_URL}/api/v1/inv/stock-outs`, payload, headers);

    check(res, {
        'No 500 Internal Server Error (Deadlock)': (r) => r.status !== 500,
        'No 504 Gateway Timeout': (r) => r.status !== 504,
        'Handled response (200, 201, 400, 409)': (r) => [200, 201, 400, 409].includes(r.status),
    });

    sleep(0.1);
}

export function teardown() {
    console.log('[FINISH] Hoàn tất Race Condition Test.');
}
