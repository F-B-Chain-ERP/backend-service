--liquibase formatted sql

--changeset erp:add-store-shift-report-and-permissions
-- =============================================================================
-- MODULE: STORE — VẬN HÀNH CỬA HÀNG & CHỐT KÉT CA KÍP (STORE OPERATIONS)
-- Phiên bản: 1.0.0
-- Tác giả: ERP-UTT Core Architecture Team
--
-- Mục tiêu changeset:
-- 1. Bổ sung các cột theo dõi dòng tiền (initial_cash, final_cash, cash_difference)
--    vào bảng `shift_assignment` để phản ánh trực tiếp trạng thái quỹ tiền mặt của ca.
-- 2. Tạo bảng mới `shift_report` lưu trữ biên bản bàn giao ca, tổng kết doanh thu
--    theo từng phương thức thanh toán (Tiền mặt, Quẹt thẻ, Chuyển khoản/QR, Ví điện tử),
--    kiểm kê tiền mặt thực tế và bảng kê chi tiết từng mệnh giá (cash denominations).
-- 3. Bổ sung Permission mới cho module STORE (báo cáo ca và phê duyệt) và gán
--    tự động cho Role ADMIN để đảm bảo phân quyền hệ thống hoạt động liền mạch.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Bổ sung cột quản lý tiền mặt cho bảng phân ca (shift_assignment)
-- -----------------------------------------------------------------------------
ALTER TABLE shift_assignment
    ADD COLUMN IF NOT EXISTS initial_cash NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS final_cash NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cash_difference NUMERIC(14,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN shift_assignment.initial_cash IS 'Số tiền mặt ban đầu nhận khi mở ca (tiền thối lẻ/quỹ ca)';
COMMENT ON COLUMN shift_assignment.final_cash IS 'Số tiền mặt thực tế kiểm đếm và bàn giao khi kết thúc ca';
COMMENT ON COLUMN shift_assignment.cash_difference IS 'Chênh lệch tiền mặt cuối ca (final_cash - expected_cash). Dương = Thừa, Âm = Thiếu, 0 = Khớp';

-- -----------------------------------------------------------------------------
-- 2. Tạo bảng biên bản chốt ca & kiểm két tiền mặt (shift_report)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shift_report (
    id                   uuid          NOT NULL,
    assignment_id        uuid          NOT NULL,
    branch_id            uuid          NOT NULL,
    business_date        date          NOT NULL,
    initial_cash         numeric(14,2) NOT NULL DEFAULT 0,
    cash_sales           numeric(14,2) NOT NULL DEFAULT 0,
    card_sales           numeric(14,2) NOT NULL DEFAULT 0,
    bank_transfer_sales  numeric(14,2) NOT NULL DEFAULT 0,
    ewallet_sales        numeric(14,2) NOT NULL DEFAULT 0,
    total_sales          numeric(14,2) NOT NULL DEFAULT 0,
    orders_count         integer       NOT NULL DEFAULT 0,
    cash_payout          numeric(14,2) NOT NULL DEFAULT 0,
    expected_cash        numeric(14,2) NOT NULL DEFAULT 0,
    actual_cash          numeric(14,2) NOT NULL DEFAULT 0,
    difference           numeric(14,2) NOT NULL DEFAULT 0,
    difference_reason    varchar(500),
    cash_denominations   text,
    status               varchar(30)   NOT NULL DEFAULT 'SUBMITTED',
    submitted_by         uuid,
    submitted_at         timestamp,
    approved_by          uuid,
    approved_at          timestamp,
    note                 varchar(500),
    created_at           timestamp     NOT NULL,
    created_by           varchar(100),
    updated_at           timestamp     NOT NULL,
    updated_by           varchar(100),

    CONSTRAINT pk_shift_report PRIMARY KEY (id),
    CONSTRAINT fk_shift_report_assignment FOREIGN KEY (assignment_id) REFERENCES shift_assignment (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_report_branch FOREIGN KEY (branch_id) REFERENCES branch (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_report_submitter FOREIGN KEY (submitted_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT fk_shift_report_approver FOREIGN KEY (approved_by) REFERENCES account (id) ON DELETE SET NULL,
    CONSTRAINT uq_shift_report_assignment UNIQUE (assignment_id),
    CONSTRAINT ck_shift_report_status CHECK (status IN ('SUBMITTED', 'CONFIRMED', 'REJECTED'))
);

-- Comments chi tiết cho bảng và các cột của shift_report
COMMENT ON TABLE shift_report IS 'Bảng lưu trữ biên bản chốt ca làm việc, đối soát doanh thu POS và kiểm kê tiền két';
COMMENT ON COLUMN shift_report.id IS 'Khóa chính định danh báo cáo ca (UUID)';
COMMENT ON COLUMN shift_report.assignment_id IS 'Khóa ngoại 1-1 liên kết với ca phân công shift_assignment';
COMMENT ON COLUMN shift_report.branch_id IS 'Chi nhánh diễn ra ca làm việc';
COMMENT ON COLUMN shift_report.business_date IS 'Ngày kinh doanh của ca làm việc';
COMMENT ON COLUMN shift_report.initial_cash IS 'Tiền mặt đầu ca (quỹ thối tiền lẻ)';
COMMENT ON COLUMN shift_report.cash_sales IS 'Tổng doanh thu thu bằng tiền mặt trong ca qua POS';
COMMENT ON COLUMN shift_report.card_sales IS 'Tổng doanh thu quẹt thẻ qua POS';
COMMENT ON COLUMN shift_report.bank_transfer_sales IS 'Tổng doanh thu chuyển khoản ngân hàng / VietQR';
COMMENT ON COLUMN shift_report.ewallet_sales IS 'Tổng doanh thu thanh toán qua ví điện tử (Momo, ZaloPay, ...)';
COMMENT ON COLUMN shift_report.total_sales IS 'Tổng doanh thu bán hàng toàn bộ các kênh trong ca';
COMMENT ON COLUMN shift_report.orders_count IS 'Tổng số lượng đơn hàng hoàn thành trong ca';
COMMENT ON COLUMN shift_report.cash_payout IS 'Khoản chi nóng tiền mặt từ két trong ca (nếu có)';
COMMENT ON COLUMN shift_report.expected_cash IS 'Tiền mặt lý thuyết trong két = initial_cash + cash_sales - cash_payout';
COMMENT ON COLUMN shift_report.actual_cash IS 'Tiền mặt thực tế đếm được trong két tại thời điểm chốt ca';
COMMENT ON COLUMN shift_report.difference IS 'Chênh lệch tiền két = actual_cash - expected_cash (Dương: Thừa, Âm: Thiếu)';
COMMENT ON COLUMN shift_report.difference_reason IS 'Giải trình nguyên nhân chênh lệch két (Bắt buộc nếu difference != 0)';
COMMENT ON COLUMN shift_report.cash_denominations IS 'Chi tiết kiểm đếm từng mệnh giá dạng JSON ([{denomination: 500000, quantity: 10, amount: 5000000}, ...])';
COMMENT ON COLUMN shift_report.status IS 'Trạng thái biên bản: SUBMITTED (Đã nộp chờ duyệt), CONFIRMED (Đã duyệt bàn giao), REJECTED (Từ chối)';
COMMENT ON COLUMN shift_report.submitted_by IS 'Tài khoản thu ngân thực hiện chốt ca và gửi biên bản';
COMMENT ON COLUMN shift_report.submitted_at IS 'Thời điểm thu ngân nộp biên bản chốt ca';
COMMENT ON COLUMN shift_report.approved_by IS 'Tài khoản Trưởng ca / Quản lý kiểm tra và xác nhận bàn giao';
COMMENT ON COLUMN shift_report.approved_at IS 'Thời điểm Trưởng ca xác nhận';
COMMENT ON COLUMN shift_report.note IS 'Ghi chú bổ sung của thu ngân hoặc trưởng ca';

-- Chỉ mục tối ưu hóa truy vấn
CREATE INDEX IF NOT EXISTS idx_shift_report_branch_date ON shift_report (branch_id, business_date);
CREATE INDEX IF NOT EXISTS idx_shift_report_status ON shift_report (status);
CREATE INDEX IF NOT EXISTS idx_shift_report_submitted_by ON shift_report (submitted_by);

-- -----------------------------------------------------------------------------
-- 3. Bổ sung Permission mới cho báo cáo ca & phân quyền cho ADMIN
-- -----------------------------------------------------------------------------
INSERT INTO permission (id, code, name, module, description, status, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'store:shift_report:view', 'View Shift Report', 'STORE', 'Xem biên bản báo cáo chốt ca', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'store:shift_report:create', 'Create Shift Report', 'STORE', 'Lập biên bản chốt ca và kiểm két', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'store:shift_report:confirm', 'Confirm Shift Report', 'STORE', 'Xác nhận duyệt biên bản bàn giao ca', 'ACTIVE', NOW(), NOW()),
    (gen_random_uuid(), 'store:daily_report:approve', 'Approve Store Daily Report', 'STORE', 'Phê duyệt khóa sổ báo cáo ngày cửa hàng', 'ACTIVE', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- Gán toàn bộ quyền mới vào Role ADMIN
INSERT INTO role_permission (role_id, permission_id)
SELECT
    'a0000000-0000-0000-0000-000000000001',
    p.id
FROM permission p
WHERE p.code IN (
                 'store:shift_report:view',
                 'store:shift_report:create',
                 'store:shift_report:confirm',
                 'store:daily_report:approve'
    )
ON CONFLICT DO NOTHING;