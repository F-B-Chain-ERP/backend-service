package com.erp.backend_service.controller;

import com.erp.backend_service.service.RoleService;
import com.erp.core.dto.auth.RoleAssignmentRequest;
import com.erp.core.dto.auth.RoleAssignmentResponse;
import com.erp.core.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý gán/thu hồi vai trò cho tài khoản (yêu cầu quyền tương ứng).
 */
@RestController
@RequestMapping("/api/v1/role-assignments")
public class RoleAssignmentController {
    private final RoleService roleService;

    public RoleAssignmentController(RoleService roleService) { this.roleService = roleService; }

    /** Gán vai trò cho tài khoản tại một phạm vi cụ thể. */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:assignment:manage')")
    public ResponseEntity<ApiResponse<RoleAssignmentResponse>> assign(@Valid @RequestBody RoleAssignmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.assign(request)));
    }

    /** Thu hồi (vô hiệu hóa) bản ghi gán vai trò theo id. */
    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasAuthority('sys:assignment:manage')")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID assignmentId) {
        roleService.revoke(assignmentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Lấy danh sách vai trò đã gán cho một tài khoản. */
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAuthority('sys:assignment:manage')")
    public ResponseEntity<ApiResponse<List<RoleAssignmentResponse>>> byAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(roleService.findByAccount(accountId)));
    }
}
