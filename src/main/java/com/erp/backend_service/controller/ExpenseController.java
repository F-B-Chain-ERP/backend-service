package com.erp.backend_service.controller;

import com.erp.backend_service.service.ExpenseService;
import com.erp.core.dto.request.fin.CreateExpenseRequest;
import com.erp.core.dto.request.fin.UpdateExpenseRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.ExpenseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * REST Controller quản lý chi phí vận hành (Expense).
 * Cung cấp API CRUD lọc theo chi nhánh/khoảng ngày/loại chi phí.
 */
@RestController
@RequestMapping("/api/v1/fin/expenses")
public class ExpenseController {

    private static final Set<String> SORTABLE_COLUMNS = Set.of("expenseDate", "amount", "createdAt");

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    /** Danh sách chi phí phân trang (lọc chi nhánh/khoảng ngày/loại chi phí/trạng thái). */
    @GetMapping
    @PreAuthorize("hasAuthority('fin:expense:view')")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "expenseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        String safeSortBy = SORTABLE_COLUMNS.contains(sortBy) ? sortBy : "expenseDate";
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.list(page, size, search, branchId, category, status,
                        dateFrom, dateTo, safeSortBy, sortDir)));
    }

    /** Chi tiết một khoản chi phí. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:expense:view')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.get(id)));
    }

    /** Ghi nhận khoản chi phí mới. */
    @PostMapping
    @PreAuthorize("hasAuthority('fin:expense:create')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> create(
            @Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(expenseService.create(request)));
    }

    /** Cập nhật khoản chi phí (chặn khi ảnh hưởng kỳ đã chốt FINALIZED). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:expense:update')")
    public ResponseEntity<ApiResponse<ExpenseResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExpenseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.update(id, request)));
    }

    /** Xóa mềm khoản chi phí (đổi trạng thái INACTIVE). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:expense:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}