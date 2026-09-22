package com.erp.backend_service.controller;

import com.erp.backend_service.service.AccountService;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.CreateAccountRequest;
import com.erp.core.dto.auth.ResetPasswordRequest;
import com.erp.core.dto.auth.UpdateAccountRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.EntityStatus;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Controller quản lý tài khoản nội bộ. Chỉ admin (ACCOUNT) mới được thực hiện;
 * tài khoản không tự đăng ký.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Tạo tài khoản nội bộ mới (do admin cấp). */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:account:create')")
    public ResponseEntity<ApiResponse<AccountResponse>> create(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Create account: username={}", request.username());
        return ResponseEntity.ok(ApiResponse.success(accountService.createAccount(request)));
    }

    /** Lấy thông tin một tài khoản theo id. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:account:view')")
    public ResponseEntity<ApiResponse<AccountResponse>> getById(@PathVariable UUID id) {
        log.info("Get {}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(id)));
    }

    /** Lấy danh sách tài khoản phân trang, hỗ trợ tìm kiếm + lọc branch/status phía server. */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:account:view')")
    public ResponseEntity<ApiResponse<PageResponse<AccountResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) EntityStatus status) {
        log.info("Get list: keyword={}, page={}, size={}, branchId={}, status={}", search, page, size, branchId, status);
        return ResponseEntity.ok(ApiResponse.success(accountService.listAccounts(page, size, search, branchId, status)));
    }

    /** Cập nhật thông tin tài khoản. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:account:update')")
    public ResponseEntity<ApiResponse<AccountResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request) {
        log.info("Update id={}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.updateAccount(id, request)));
    }

    /** Vô hiệu hóa (xóa mềm) tài khoản. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:account:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("Delete id={}", id);
        accountService.deleteAccount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Đặt lại mật khẩu cho tài khoản. */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('sys:account:update')")
    public ResponseEntity<ApiResponse<AccountResponse>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        log.info("Reset password id={}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.resetPassword(id, request)));
    }
}
