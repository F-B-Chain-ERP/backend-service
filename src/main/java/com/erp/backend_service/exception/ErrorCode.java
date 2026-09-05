package com.erp.backend_service.exception;

/**
 * Định nghĩa tập hợp các mã lỗi nghiệp vụ dùng chung trong hệ thống,
 * mỗi mã gồm HTTP status, code và thông báo mặc định.
 */
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(500, "ERR_500", "Lỗi không xác định"),
    INVALID_KEY(400, "ERR_400", "Khóa không hợp lệ"),
    BAD_REQUEST(400, "ERR_400_BAD_REQUEST", "Yêu cầu không hợp lệ"),
    INVALID_REQUEST(400, "ERR_400_INVALID_REQUEST", "Yêu cầu không hợp lệ"),
    DUPLICATE_RESOURCE(409, "ERR_409_DUPLICATE", "Tài nguyên đã tồn tại"),
    RESOURCE_NOT_FOUND(404, "ERR_404_NOT_FOUND", "Không tìm thấy tài nguyên"),
    USER_EXISTED(400, "ERR_400_USER_EXISTED", "Người dùng đã tồn tại"),
    USER_NOT_EXISTED(404, "ERR_404_USER_NOT_EXISTED", "Người dùng không tồn tại"),
    UNAUTHENTICATED(401, "ERR_401", "Chưa xác thực"),
    UNAUTHORIZED(403, "ERR_403", "Bạn không có quyền truy cập"),
    INVALID_TOKEN(401, "ERR_401_INVALID_TOKEN", "Token không hợp lệ hoặc đã hết hạn"),
    TOKEN_REVOKED(401, "ERR_401_TOKEN_REVOKED", "Token đã bị thu hồi"),
    ACCOUNT_DISABLED(403, "ERR_403_ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa hoặc tạm khóa"),
    TOO_MANY_REQUESTS(429, "ERR_429", "Quá nhiều yêu cầu, vui lòng thử lại sau"),
    BAD_CREDENTIALS(401, "ERR_401_BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(401, "ACCOUNT_LOCKED", "Tài khoản tạm thời bị khóa"),
    ASSIGNMENT_REQUIRED_FIELDS(400, "ASSIGNMENT_REQUIRED_FIELDS", "Tài khoản, vai trò và phạm vi là bắt buộc"),
    ASSIGNMENT_EXISTS(409, "ASSIGNMENT_EXISTS", "Phân vai trò và phạm vi đã tồn tại"),
    ACCOUNT_NOT_FOUND(404, "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản"),
    CUSTOMER_NOT_FOUND(404, "CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng"),
    ACCOUNT_INACTIVE(401, "ACCOUNT_INACTIVE", "Tài khoản không hoạt động"),
    ROLE_NOT_FOUND(404, "ROLE_NOT_FOUND", "Không tìm thấy vai trò"),
    SCOPE_NOT_FOUND(404, "SCOPE_NOT_FOUND", "Không tìm thấy phạm vi"),
    PERMISSION_DENIED(403, "PERMISSION_DENIED", "Không có quyền thực hiện"),
    CROSS_SCOPE_DENIED(403, "CROSS_SCOPE_DENIED", "Dữ liệu nằm ngoài phạm vi được phân quyền"),
    CANNOT_MODIFY_ADMIN(400, "CANNOT_MODIFY_ADMIN", "Không thể thay đổi phân quyền của quản trị viên gốc"),
    ACCOUNT_SESSION_REVOKED(401, "ACCOUNT_SESSION_REVOKED", "Phiên đăng nhập của tài khoản đã bị thu hồi"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Lỗi dịch vụ nội bộ"),
    EMAIL_REQUIRED(400, "ERR_400_EMAIL_REQUIRED", "Cần cung cấp email để xác thực tài khoản"),
    OTP_INVALID(400, "ERR_400_OTP_INVALID", "Mã xác thực không hợp lệ"),
    OTP_EXPIRED(400, "ERR_400_OTP_EXPIRED", "Mã xác thực đã hết hạn, vui lòng yêu cầu mã mới"),
    OTP_ATTEMPTS_EXCEEDED(429, "ERR_429_OTP_ATTEMPTS", "Quá số lần thử sai, vui lòng yêu cầu mã mới"),
    PHONE_EXISTED(400, "ERR_400_PHONE_EXISTED", "Số điện thoại đã tồn tại"),
    EMAIL_EXISTED(400, "ERR_400_EMAIL_EXISTED", "Email đã tồn tại"),
    SUPPLIER_NOT_FOUND(404, "PROC_404_SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp"),
    SUPPLIER_CODE_EXISTED(400, "PROC_400_SUPPLIER_CODE_EXISTED", "Mã nhà cung cấp đã được sử dụng"),
    SUPPLIER_TAX_CODE_EXISTED(400, "PROC_400_SUPPLIER_TAX_CODE_EXISTED", "Mã số thuế đã được sử dụng bởi nhà cung cấp khác"),
    MATERIAL_NOT_FOUND(404,"PROC_404_MATERIAL_NOT_FOUND", "Không tìm thấy nguyên vật liệu"),
    SUPPLIER_MATERIAL_NOT_FOUND(404, "PROC_404_SUPPLIER_NOT_FOUND", "Không tìm thấy nguyên vật liệu của nhà cung cấp"),
    SUPPLIER_MATERIAL_EXISTS(409, "PROC_409_SUPPLIER_MATERIAL_EXISTS", "Nhà cung cấp đã được liên kết với nguyên vật liệu này"),
    PROC_404_PO_NOT_FOUND(404, "PROC_404_PO_NOT_FOUND", "Đơn mua hàng không tồn tại."),
    PROC_400_PO_INVALID_STATUS_FOR_EDIT(400, "PROC_400_PO_INVALID_STATUS_FOR_EDIT", "Chỉ có thể chỉnh sửa đơn mua hàng ở trạng thái DRAFT."),
    PROC_400_PO_INVALID_STATUS_FOR_SUBMIT(400, "PROC_400_PO_INVALID_STATUS_FOR_SUBMIT", "Chỉ có thể trình duyệt đơn ở trạng thái DRAFT."),
    PROC_400_PO_INVALID_STATUS_FOR_APPROVE(400, "PROC_400_PO_INVALID_STATUS_FOR_APPROVE", "Chỉ có thể phê duyệt đơn ở trạng thái SUBMITTED."),
    PROC_400_PO_INVALID_STATUS_FOR_REJECT(400, "PROC_400_PO_INVALID_STATUS_FOR_REJECT", "Chỉ có thể từ chối đơn đang SUBMITTED."),
    PROC_400_PO_INVALID_STATUS_FOR_CANCEL(400, "PROC_400_PO_INVALID_STATUS_FOR_CANCEL", "Không thể hủy đơn mua hàng ở trạng thái hiện tại."),
    PROC_400_PO_INVALID_STATUS_FOR_RECEIVE(400, "PROC_400_PO_INVALID_STATUS_FOR_RECEIVE", "Chỉ có thể ghi nhận nhận hàng khi đơn ở trạng thái APPROVED hoặc PARTIALLY_RECEIVED."),
    PROC_400_PO_ITEMS_EMPTY(400, "PROC_400_PO_ITEMS_EMPTY", "Đơn đặt mua hàng phải có ít nhất một mặt hàng."),
    PROC_400_PO_INVALID_ITEM(400, "PROC_400_PO_INVALID_ITEM", "Dòng nguyên vật liệu không hợp lệ."),
    PROC_400_PO_INVALID_FILTER(400, "PROC_400_PO_INVALID_FILTER", "Khoảng ngày hoặc giá trị lọc không hợp lệ."),
    PROC_404_SUPPLIER_NOT_FOUND(404, "PROC_404_SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp."),
    PROC_400_SUPPLIER_INACTIVE(400, "PROC_400_SUPPLIER_INACTIVE", "Nhà cung cấp đang ngừng hoạt động."),
    PROC_404_WAREHOUSE_NOT_FOUND(404, "PROC_404_WAREHOUSE_NOT_FOUND", "Không tìm thấy kho nhận."),
    PROC_400_WAREHOUSE_INACTIVE(400, "PROC_400_WAREHOUSE_INACTIVE", "Kho nhận đang ngừng hoạt động."),
    PROC_400_PO_INVALID_ORDER_DATE(400, "PROC_400_PO_INVALID_ORDER_DATE", "Ngày đặt hàng không hợp lệ."),
    PROC_400_PO_INVALID_EXPECTED_DATE(400, "PROC_400_PO_INVALID_EXPECTED_DATE", "Ngày dự kiến nhận phải bằng hoặc sau ngày đặt hàng."),
    PROC_400_PO_RECEIVED_EXCEED(400, "PROC_400_PO_RECEIVED_EXCEED", "Số lượng nhận vượt quá số lượng đặt còn lại của dòng hàng."),
    PROC_400_PO_CANCEL_REASON_REQUIRED(400, "PROC_400_PO_CANCEL_REASON_REQUIRED", "Vui lòng nhập lý do hủy đơn."),
    PROC_400_PO_REJECT_REASON_REQUIRED(400, "PROC_400_PO_REJECT_REASON_REQUIRED", "Vui lòng nhập lý do từ chối."),
    INV_400_MATERIAL_CODE_EXISTED(400, "INV_400_MATERIAL_CODE_EXISTED", "Mã nguyên vật liệu đã tồn tại."),
    INV_404_CATEGORY_NOT_FOUND(404, "INV_404_CATEGORY_NOT_FOUND", "Không tìm thấy danh mục."),
    INV_404_UNIT_NOT_FOUND(404, "INV_404_UNIT_NOT_FOUND", "Không tìm thấy đơn vị tính."),
    INV_400_INVALID_MATERIAL_CATEGORY(400, "INV_400_INVALID_MATERIAL_CATEGORY", "Danh mục phải thuộc nhóm MATERIAL."),
    INV_400_INVALID_MATERIAL_DATA(400, "INV_400_INVALID_MATERIAL_DATA", "Dữ liệu nguyên vật liệu không hợp lệ."),
    INV_400_MATERIAL_INVALID_STATUS(400, "INV_400_MATERIAL_INVALID_STATUS", "Trạng thái nguyên vật liệu không hợp lệ."),
    INV_400_MATERIAL_IN_USE(400, "INV_400_MATERIAL_IN_USE", "Nguyên vật liệu đã phát sinh dữ liệu và không thể xóa. Vui lòng chuyển sang INACTIVE."),
    INV_404_WAREHOUSE_NOT_FOUND(404, "INV_404_WAREHOUSE_NOT_FOUND", "Không tìm thấy kho hàng."),
    INV_409_WAREHOUSE_CODE_EXISTED(409, "INV_409_WAREHOUSE_CODE_EXISTED", "Mã kho đã tồn tại."),
    INV_400_WAREHOUSE_IN_USE(400, "INV_400_WAREHOUSE_IN_USE", "Kho đã phát sinh dữ liệu (đơn mua hàng/tồn kho) và không thể xóa. Vui lòng chuyển sang INACTIVE."),
    INV_400_WAREHOUSE_INVALID_STATUS(400, "INV_400_WAREHOUSE_INVALID_STATUS", "Trạng thái kho không hợp lệ."),
    INV_404_BRANCH_NOT_FOUND(404, "INV_404_BRANCH_NOT_FOUND", "Không tìm thấy chi nhánh."),
    PERMISSION_NOT_FOUND(404, "PERMISSION_NOT_FOUND", "Permission not found"),
    PERMISSION_CODE_EXISTS(409, "PERMISSION_CODE_EXISTS", "Permission code already exists"),
    PERMISSION_IN_USE(409, "PERMISSION_IN_USE", "Permission is still assigned to one or more roles"),
    SCOPE_IN_USE(409, "SCOPE_IN_USE", "Phạm vi vẫn đang được gán cho một hoặc nhiều tài khoản"),;


    private final int statusCode;
    private final String code;
    private final String message;

    ErrorCode(int statusCode, String code, String message) {
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
    }

    /** Lấy HTTP status code tương ứng với mã lỗi. */
    public int getStatusCode() {
        return statusCode;
    }

    /** Lấy mã lỗi nội bộ (ví dụ: ERR_401). */
    public String getCode() {
        return code;
    }

    /** Lấy thông báo mặc định của mã lỗi. */
    public String getMessage() {
        return message;
    }
}
