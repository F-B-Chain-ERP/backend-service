package com.erp.backend_service.controller;

import com.erp.backend_service.service.AccountsPayableService;
import com.erp.core.dto.request.fin.CreateAccountsPayableRequest;
import com.erp.core.dto.request.fin.CreatePayablePaymentRequest;
import com.erp.core.dto.request.fin.UpdateAccountsPayableRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.AccountsPayableDetailResponse;
import com.erp.core.dto.response.fin.AccountsPayableSummaryResponse;
import com.erp.core.dto.response.fin.PayablePaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST Controller quản lý công nợ phải trả (Accounts Payable).
 * Cung cấp API CRUD, ghi nhận thanh toán và truy vấn lịch sử thanh toán.
 */
@RestController
@RequestMapping("/api/v1/fin/payables")
public class AccountsPayableController {

    private final AccountsPayableService accountsPayableService;

    public AccountsPayableController(AccountsPayableService accountsPayableService) {
        this.accountsPayableService = accountsPayableService;
    }

    /** Danh sách công nợ phân trang (lọc NCC/khoảng hạn, sort dueDate/remaining/createdAt). */
    @GetMapping
    @PreAuthorize("hasAuthority('fin:payable:view')")
    public ResponseEntity<ApiResponse<PageResponse<AccountsPayableSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(10) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        String safeSortBy = Set.of("dueDate", "remaining", "createdAt").contains(sortBy) ? sortBy : "dueDate";
        return ResponseEntity.ok(ApiResponse.success(
                accountsPayableService.list(page, size, search, status, supplierId,
                        dueFrom, dueTo, safeSortBy, sortDir)));
    }

    /** Chi tiết một công nợ kèm danh sách thanh toán. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:payable:view')")
    public ResponseEntity<ApiResponse<AccountsPayableDetailResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountsPayableService.get(id)));
    }

    /** Tạo mới công nợ thủ công. */
    @PostMapping
    @PreAuthorize("hasAuthority('fin:payable:create')")
    public ResponseEntity<ApiResponse<AccountsPayableSummaryResponse>> create(
            @Valid @RequestBody CreateAccountsPayableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountsPayableService.create(request)));
    }

    /** Cập nhật công nợ (chỉ UNPAID chưa có HĐ và chưa có thanh toán). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:payable:update')")
    public ResponseEntity<ApiResponse<AccountsPayableSummaryResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountsPayableRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountsPayableService.update(id, request)));
    }

    /** Xóa công nợ (chỉ UNPAID chưa có thanh toán). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:payable:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        accountsPayableService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Ghi nhận một lần thanh toán cho công nợ. */
    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('fin:payable_payment:create')")
    public ResponseEntity<ApiResponse<PayablePaymentResponse>> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePayablePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountsPayableService.recordPayment(id, request)));
    }

    /** Lấy danh sách thanh toán của một công nợ (read-only). */
    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('fin:payable_payment:view')")
    public ResponseEntity<ApiResponse<List<PayablePaymentResponse>>> getPayments(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountsPayableService.getPayments(id)));
    }

    /** Tổng quan KPI: tổng nợ còn phải trả và tổng nợ quá hạn. */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('fin:payable:view')")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> summary() {
        return ResponseEntity.ok(ApiResponse.success(accountsPayableService.getSummary()));
    }

    /** Danh sách purchaseOrderId đã có trong accounts_payable. */
    @GetMapping("/existing-po-ids")
    @PreAuthorize("hasAuthority('fin:payable:view')")
    public ResponseEntity<ApiResponse<Set<UUID>>> existingPoIds() {
        return ResponseEntity.ok(ApiResponse.success(accountsPayableService.getExistingPoIds()));
    }

    /** Quét và đánh dấu công nợ quá hạn (gọi khi refresh để fix data realtime). */
    @PostMapping("/overdue/trigger")
    @PreAuthorize("hasAuthority('fin:payable:update')")
    public ResponseEntity<ApiResponse<Void>> triggerOverdueCheck() {
        accountsPayableService.checkAndMarkOverdue();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
