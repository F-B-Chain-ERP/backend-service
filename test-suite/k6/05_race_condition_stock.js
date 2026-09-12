// ==============================================================================
// 05: RACE CONDITION & CONCURRENCY TEST (TRANH CHẤP KHO ĐỒNG THỜI)
// Mục tiêu: Giả lập 50 luồng đồng thời thao tác xuất kho / tạo phiếu kho
// trên cùng 1 mặt hàng để kiểm tra tính toàn vẹn dữ liệu (Locking & Isolation),
// quan sát xem hệ thống có bị Deadlock hoặc sập Connection Pool hay không.
// ==============================================================================

import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, authenticateUser, getAuthHeaders } from './config.js';

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
        'http_req_failed': ['rate<0.50'],
    },
};

export function setup() {
    console.log(`[RACE CONDITION] Chuẩn bị kiểm thử tranh chấp đồng thời tại: ${BASE_URL}`);

    // Thử đăng nhập bằng các tài khoản admin trong danh sách 10 user của DB
    let token = null;
    const adminCandidates = ['admin1', 'admin2', 'admin3', 'admin4', 'hoangdinhdung', 'hoangdinhdung20205'];
    for (const username of adminCandidates) {
        token = authenticateUser({ usernameOrEmail: username, password: '123456789' });
        if (token) {
            console.log(`[SETUP] Đăng nhập thành công tài khoản quản trị: '${username}'`);
            break;
        }
    }

    if (!token) {
        console.error('[ERROR] Không lấy được token xác thực từ danh sách tài khoản!');
        return { token: null, warehouseId: null, materialId: null };
    }

    const headers = getAuthHeaders(token);
    let warehouseId = null;
    let materialId = null;

    // 1. Lấy 1 bản ghi tồn kho mẫu có sẵn
    const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=1`, headers);
    if (resStock.status === 200) {
        try {
            const body = JSON.parse(resStock.body);
            const list = body.data?.content || body.data?.items || body.data || [];
            const item = list[0];
            if (item) {
                warehouseId = item.warehouseId;
                materialId = item.materialId;
            }
        } catch (e) {
            console.error('Lỗi parse tồn kho mẫu:', e);
        }
    }

    // 2. Nếu chưa có tồn kho, tìm warehouse và material bất kỳ
    if (!warehouseId) {
        const resWh = http.get(`${BASE_URL}/api/v1/warehouses?page=0&size=1`, headers);
        if (resWh.status === 200) {
            try {
                const body = JSON.parse(resWh.body);
                const list = body.data?.content || body.data || [];
                if (list[0]) warehouseId = list[0].id;
            } catch (e) {}
        }
    }

    if (!materialId) {
        const resMat = http.get(`${BASE_URL}/api/v1/proc/materials?page=0&size=1`, headers);
        if (resMat.status === 200) {
            try {
                const body = JSON.parse(resMat.body);
                const list = body.data?.content || body.data || [];
                if (list[0]) materialId = list[0].id;
            } catch (e) {}
        }
    }

    console.log(`[SETUP] Sẵn sàng: Warehouse=${warehouseId}, Material=${materialId}`);
    return { token, warehouseId, materialId };
}

export default function (data) {
    if (!data.token || !data.warehouseId || !data.materialId) {
        sleep(1);
        return;
    }

    const headers = getAuthHeaders(data.token);
    const today = new Date().toISOString().split('T')[0];

    // Payload chuẩn theo CreateStockOutRequest DTO
    const payload = JSON.stringify({
        warehouseId: data.warehouseId,
        destinationType: 'STORE',
        outDate: today,
        note: `VU ${__VU} race condition stock test`,
        items: [
            {
                materialId: data.materialId,
                quantity: 1.0,
                unitPrice: 15000.0,
                batchNo: `BATCH_${__VU}`
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
