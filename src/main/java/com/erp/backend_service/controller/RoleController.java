package com.erp.backend_service.controller;

import com.erp.backend_service.service.RoleService;
import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.auth.RoleMemberResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@Validated
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:create')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<RoleResponse>> getById(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id) {
        RoleResponse response = roleService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        PageResponse<RoleResponse> response = roleService.getAll(page, size, search);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:update')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        RoleResponse response = roleService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Lấy danh sách id quyền đã gán cho một vai trò. */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<List<UUID>>> getPermissions(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getPermissionsByRole(id)));
    }

    /** Lấy danh sách tài khoản là thành viên của một vai trò. */
    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public ResponseEntity<ApiResponse<List<RoleMemberResponse>>> getMembers(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getMembers(id)));
    }

    /** Thay thế toàn bộ quyền của một vai trò. */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('sys:role_permission:create')")
    public ResponseEntity<ApiResponse<Void>> setPermissions(
            @NotNull(message = "Role id must not be null")
            @PathVariable UUID id,
            @RequestBody List<UUID> permissionIds) {
        roleService.setPermissionsForRole(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
