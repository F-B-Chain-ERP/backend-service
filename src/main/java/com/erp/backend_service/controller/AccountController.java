package com.erp.backend_service.controller;

import com.erp.backend_service.service.AccountService;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.ResetPasswordRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
 * Controller quản lý tài khoản nội bộ. Chỉ admin (ACCOUNT) mới được thực hiện;
 * tài khoản không tự đăng ký.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Tạo tài khoản nội bộ mới (do admin cấp). */
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.createAccount(request)));
    }

    /** Lấy thông tin một tài khoản theo id. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(id)));
    }

    /** Lấy danh sách tài khoản phân trang, hỗ trợ tìm kiếm. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccountResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(accountService.listAccounts(page, size, search)));
    }

    /** Cập nhật thông tin tài khoản. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.updateAccount(id, request)));
    }

    /** Vô hiệu hóa (xóa mềm) tài khoản. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        accountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Đặt lại mật khẩu cho tài khoản. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<AccountResponse>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.resetPassword(id, request)));
    }
}
