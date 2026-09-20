package com.erp.backend_service.service;

import com.erp.core.dto.request.fin.CreateAccountsPayableRequest;
import com.erp.core.dto.request.fin.CreatePayablePaymentRequest;
import com.erp.core.dto.request.fin.UpdateAccountsPayableRequest;
import com.erp.core.dto.response.fin.AccountsPayableDetailResponse;
import com.erp.core.dto.response.fin.AccountsPayableSummaryResponse;
import com.erp.core.dto.response.fin.PayablePaymentResponse;
import com.erp.core.dto.response.PageResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý công nợ phải trả: truy vấn, tạo, cập nhật, xóa,
 * ghi nhận thanh toán và kiểm tra quá hạn.
 */
public interface AccountsPayableService {

    /** Danh sách công nợ phân trang (lọc hạn thanh toán, sort dueDate/remaining/createdAt). */
    PageResponse<AccountsPayableSummaryResponse> list(int page, int size, String search,
                                                      String status, UUID supplierId,
                                                      java.time.LocalDate dueFrom, java.time.LocalDate dueTo,
                                                      String sortBy, String sortDir);

    /** Chi tiết một công nợ kèm danh sách thanh toán. */
    AccountsPayableDetailResponse get(UUID id);

    /** Tạo mới công nợ thủ công. */
    AccountsPayableSummaryResponse create(CreateAccountsPayableRequest request);

    /** Cập nhật công nợ (chỉ UNPAID chưa có HĐ và chưa có thanh toán). */
    AccountsPayableSummaryResponse update(UUID id, UpdateAccountsPayableRequest request);

    /** Xóa công nợ (chỉ UNPAID chưa có thanh toán). */
    void delete(UUID id);

    /** Ghi nhận một lần thanh toán cho công nợ. */
    PayablePaymentResponse recordPayment(UUID payableId, CreatePayablePaymentRequest request);

    /** Lấy danh sách thanh toán của một công nợ (read-only). */
    List<PayablePaymentResponse> getPayments(UUID payableId);

    /** Kiểm tra và đánh dấu các công nợ quá hạn. */
    void checkAndMarkOverdue();

    /** Tạo công nợ tự động từ PO khi nhận hàng. */
    AccountsPayableSummaryResponse createFromPo(UUID purchaseOrderId);

    /** Tong quan KPI: tong no con phai tra va tong no qua han. */
    Map<String, BigDecimal> getSummary();

    /** Danh sach purchaseOrderId da co trong accounts_payable. */
    Set<UUID> getExistingPoIds();
}
