// ==============================================================================
// 06: ENTERPRISE DRILL - KỊCH BẢN DIỄN TẬP TẢI TOÀN DIỆN ĐA CHI NHÁNH
// ==============================================================================
// Mô phỏng 1 ca bán hàng giờ cao điểm tại chuỗi F&B với 1.000 VUs phân bổ theo
// 4 nhóm vai trò (Personas) thực tế:
//   1. Khách hàng (40% VUs): Lướt menu, tìm kiếm món, xem chi nhánh
//   2. Thu ngân POS (30% VUs): Login Cashier, tra cứu menu giá, tìm khách hàng
//   3. Thủ kho (20% VUs): Login Warehouse, tra cứu tồn kho, xem phiếu xuất nhập
//   4. Quản lý / Mua hàng (10% VUs): Login Manager, duyệt đơn PO, kiểm tra nhà cung cấp
// ==============================================================================

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import {
    BASE_URL,
    getAllAuthenticatedTokens,
    filterTokensByRole,
    getAuthHeaders,
    getPublicHeaders
} from './config.js';

export const options = {
    scenarios: {
        enterprise_peak_hours: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '30s', target: 100 },   // Khởi động nhẹ đầu ca
                { duration: '1m', target: 300 },    // Khách bắt đầu đông
                { duration: '2m', target: 500 },    // Giờ cao điểm
                { duration: '2m', target: 1000 },   // Đỉnh điểm quá tải (Peak Spike)
                { duration: '2m', target: 1000 },   // Duy trì áp lực 1.000 VUs
                { duration: '1m', target: 100 },    // Hạ tải
                { duration: '30s', target: 0 },     // Kết thúc ca
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<3000'],       // 95% request phải xong dưới 3s
        'http_req_failed': ['rate<0.15'],          // Tỷ lệ lỗi toàn hệ thống < 15%
    },
};

export function setup() {
    console.log(`[ENTERPRISE DRILL] Bắt đầu chuẩn bị diễn tập tại: ${BASE_URL}`);
    // Thu thập trước tới 150 tokens từ các tài khoản nhân viên đa chi nhánh
    const allTokens = getAllAuthenticatedTokens(150);

    const cashiers = filterTokensByRole(allTokens, 'CASHIER');
    const warehouses = filterTokensByRole(allTokens, 'WAREHOUSE');
    const managers = filterTokensByRole(allTokens, 'MANAGER');
    const admins = filterTokensByRole(allTokens, 'ADMIN');

    console.log(`[SETUP] Phân loại nhân sự: ${cashiers.length} Cashiers, ${warehouses.length} Warehouse, ${managers.length} Managers, ${admins.length} Admins.`);

    return {
        allTokens: allTokens,
        cashierTokens: cashiers,
        warehouseTokens: warehouses,
        managerTokens: managers.length > 0 ? managers : admins,
    };
}

export default function (data) {
    // Xác định nhóm Persona dựa trên __VU ID để đạt đúng tỷ lệ phân bổ:
    // 40% Khách hàng (0-39), 30% Thu ngân (40-69), 20% Thủ kho (70-89), 10% Quản lý (90-99)
    const personaBucket = (__VU % 100);

    if (personaBucket < 40) {
        // =====================================================================
        // PERSONA 1: KHÁCH HÀNG VÃNG LAI (40% TRAFFIC)
        // =====================================================================
        group('Persona 1: Customer Browsing Menu', function () {
            const pubHeaders = getPublicHeaders();

            // 1. Xem danh mục đồ uống đang bán
            const resCats = http.get(`${BASE_URL}/api/v1/sales/categories`, pubHeaders);
            check(resCats, {
                'Customer: GET /sales/categories is 200': (r) => r.status === 200,
            });

            // 2. Phân trang menu sản phẩm
            const page = Math.floor(Math.random() * 4);
            const resProds = http.get(`${BASE_URL}/api/v1/sales/products?page=${page}&size=10`, pubHeaders);
            check(resProds, {
                'Customer: GET /sales/products is 200': (r) => r.status === 200,
                'Customer: Not 429 Too Many Requests': (r) => r.status !== 429,
            });

            // 3. Tìm kiếm đồ uống yêu thích
            const keywords = ['Trà', 'Cà phê', 'Matcha', 'Sữa', 'Trân châu', 'Đào'];
            const kw = keywords[Math.floor(Math.random() * keywords.length)];
            const resSearch = http.get(`${BASE_URL}/api/v1/sales/products?search=${encodeURIComponent(kw)}`, pubHeaders);
            check(resSearch, {
                'Customer: Search /sales/products is 200': (r) => r.status === 200,
            });

            // 4. Tra cứu danh sách chi nhánh cửa hàng
            const resBranches = http.get(`${BASE_URL}/api/v1/branches`, pubHeaders);
            check(resBranches, {
                'Customer: GET /branches is 200': (r) => r.status === 200,
            });
        });

    } else if (personaBucket < 70) {
        // =====================================================================
        // PERSONA 2: THU NGÂN POS BÁN HÀNG (30% TRAFFIC)
        // =====================================================================
        group('Persona 2: Cashier POS Operations', function () {
            const tokens = data.cashierTokens.length > 0 ? data.cashierTokens : data.allTokens;
            if (!tokens || tokens.length === 0) return;

            const session = tokens[__VU % tokens.length];
            const headers = getAuthHeaders(session.token, session.branchId);

            // 1. Kiểm tra tài khoản & ca làm việc
            const resMe = http.get(`${BASE_URL}/api/v1/accounts/me`, headers);
            check(resMe, {
                'POS: GET /accounts/me is 200': (r) => r.status === 200,
            });

            // 2. Tải toàn bộ menu bán hàng vào màn hình POS (kèm branch cache)
            const resMenu = http.get(`${BASE_URL}/api/v1/sales/products?page=0&size=50`, headers);
            check(resMenu, {
                'POS: Load Menu is 200': (r) => r.status === 200,
                'POS: Not 429 RateLimit': (r) => r.status !== 429,
            });

            // 3. Tra cứu khách hàng thân thiết theo số điện thoại
            const resCust = http.get(`${BASE_URL}/api/v1/customers?page=0&size=5`, headers);
            check(resCust, {
                'POS: Lookup Customers is 200': (r) => r.status === 200,
            });

            // 4. Kiểm tra tồn kho nguyên liệu nhanh tại quầy pha chế
            const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=10`, headers);
            check(resStock, {
                'POS: Check Bar Stock is 200': (r) => r.status === 200,
            });
        });

    } else if (personaBucket < 90) {
        // =====================================================================
        // PERSONA 3: THỦ KHO CHI NHÁNH (20% TRAFFIC)
        // =====================================================================
        group('Persona 3: Warehouse Operations', function () {
            const tokens = data.warehouseTokens.length > 0 ? data.warehouseTokens : data.allTokens;
            if (!tokens || tokens.length === 0) return;

            const session = tokens[__VU % tokens.length];
            const headers = getAuthHeaders(session.token, session.branchId);

            // 1. Danh sách kho hàng
            const resWh = http.get(`${BASE_URL}/api/v1/inv/warehouses`, headers);
            check(resWh, {
                'WH: GET /inv/warehouses is 200': (r) => r.status === 200,
            });

            // 2. Tra cứu biến động tồn kho chi nhánh (Query JOIN DB lớn)
            const page = Math.floor(Math.random() * 3);
            const resStock = http.get(`${BASE_URL}/api/v1/inv/stocks?page=${page}&size=20`, headers);
            check(resStock, {
                'WH: GET /inv/stocks is 200': (r) => r.status === 200,
                'WH: Not 5xx Error': (r) => r.status < 500,
                'WH: Not 429 RateLimit': (r) => r.status !== 429,
            });

            // 3. Lịch sử phiếu xuất kho
            const resOut = http.get(`${BASE_URL}/api/v1/inv/stock-outs?page=0&size=10`, headers);
            check(resOut, {
                'WH: GET /inv/stock-outs is 200': (r) => r.status === 200,
            });
        });

    } else {
        // =====================================================================
        // PERSONA 4: QUẢN LÝ CỬA HÀNG / MUA HÀNG (10% TRAFFIC)
        // =====================================================================
        group('Persona 4: Store Manager & Procurement', function () {
            const tokens = data.managerTokens.length > 0 ? data.managerTokens : data.allTokens;
            if (!tokens || tokens.length === 0) return;

            const session = tokens[__VU % tokens.length];
            const headers = getAuthHeaders(session.token, session.branchId);

            // 1. Tra cứu đơn mua hàng PO
            const resPo = http.get(`${BASE_URL}/api/v1/proc/purchase-orders?page=0&size=10`, headers);
            check(resPo, {
                'Manager: GET /proc/purchase-orders is 200': (r) => r.status === 200,
            });

            // 2. Tra cứu danh sách nhà cung cấp
            const resSuppliers = http.get(`${BASE_URL}/api/v1/proc/suppliers?page=0&size=10`, headers);
            check(resSuppliers, {
                'Manager: GET /proc/suppliers is 200': (r) => r.status === 200,
            });

            // 3. Báo cáo tổng thể tồn kho (Size lớn 50 items)
            const resStockReport = http.get(`${BASE_URL}/api/v1/inv/stocks?page=0&size=50`, headers);
            check(resStockReport, {
                'Manager: GET /inv/stocks (report) is 200': (r) => r.status === 200,
            });
        });
    }

    // Thời gian nghỉ tự nhiên giữa các thao tác (Pacing 500ms - 1.5s)
    sleep(0.5 + Math.random());
}
