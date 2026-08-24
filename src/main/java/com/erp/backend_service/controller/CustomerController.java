package com.erp.backend_service.controller;

import com.erp.backend_service.service.CustomerService;
import com.erp.core.dto.auth.CreateCustomerRequest;
import com.erp.core.dto.auth.ResetCustomerPasswordRequest;
import com.erp.core.dto.auth.UpdateCustomerRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.customer.CustomerDetailResponse;
import com.erp.core.dto.response.PageResponse;
import jakarta.validation.Valid;
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

import java.util.UUID;

/**
 * Controller quản lý tài khoản khách hàng (customer) bởi admin nội bộ.
 * Khách hàng hoàn toàn tách biệt với tài khoản nội bộ (account).
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Tạo tài khoản khách hàng mới (do admin cấp). */
    @PostMapping
    @PreAuthorize("hasAuthority('customer:create')")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.createCustomer(request)));
    }

    /** Lấy thông tin một khách hàng theo id. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:view')")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getCustomer(id)));
    }

    /** Lấy danh sách khách hàng phân trang, hỗ trợ tìm kiếm. */
    @GetMapping
    @PreAuthorize("hasAuthority('customer:view')")
    public ResponseEntity<ApiResponse<PageResponse<CustomerDetailResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(customerService.listCustomers(page, size, search)));
    }

    /** Cập nhật thông tin khách hàng. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:update')")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.updateCustomer(id, request)));
    }

    /** Vô hiệu hóa (xóa mềm) khách hàng. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Đặt lại mật khẩu cho khách hàng. */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('customer:update')")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetCustomerPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.resetPassword(id, request)));
    }
}
